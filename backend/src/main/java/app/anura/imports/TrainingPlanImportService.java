package app.anura.imports;

import static app.anura.imports.ImportDtos.*;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrainingPlanImportService {
  private final JdbcTemplate db;
  private final int maxRows;
  private final long maxSize;
  private final long ttlHours;

  TrainingPlanImportService(
      JdbcTemplate db,
      @Value("${app.imports.max-rows:2000}") int maxRows,
      @Value("${app.imports.max-file-size:1048576}") long maxSize,
      @Value("${app.imports.job-ttl-hours:24}") long ttlHours) {
    this.db = db;
    this.maxRows = maxRows;
    this.maxSize = maxSize;
    this.ttlHours = ttlHours;
  }

  @Transactional
  public Preview preview(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > maxSize)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE", "Archivo vacío o demasiado grande");
    byte[] bytes;
    Parsed parsed;
    try {
      bytes = file.getBytes();
      parsed = parse(new String(bytes, StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_CSV",
          "No se pudo leer el CSV como UTF-8 separado por punto y coma");
    }
    String content = new String(bytes, StandardCharsets.UTF_8);
    String checksum;
    try { checksum = sha256(bytes); }
    catch (Exception e) { throw new IllegalStateException("SHA-256 no disponible", e); }
    UUID user = CurrentUser.id();
    List<UUID> previous = db.query(
        "SELECT id FROM import_job WHERE user_id=? AND import_type='TRAINING_PLAN' AND checksum=? AND expires_at>CURRENT_TIMESTAMP ORDER BY created_at DESC",
        (rs, n) -> rs.getObject(1, UUID.class), user, checksum);
    if (!previous.isEmpty()) return parsed.preview(previous.getFirst());
    UUID job = UUID.randomUUID();
    int inserted=db.update(
        "INSERT INTO import_job(id,user_id,import_type,schema_version,status,original_filename,checksum,file_size,content,external_id,plan_version,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        job,user,"TRAINING_PLAN","v1",parsed.issues.isEmpty()?"VALID":"INVALID",safeName(file.getOriginalFilename()),checksum,file.getSize(),content,parsed.externalId,parsed.version,java.sql.Timestamp.from(Instant.now().plusSeconds(ttlHours*3600)));
    if(inserted==0){
      UUID existing=db.query("SELECT id FROM import_job WHERE user_id=? AND import_type='TRAINING_PLAN' AND checksum=? ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getObject(1,UUID.class),user,checksum).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"IMPORT_CONFLICT","La previsualización ya se está procesando"));
      String existingStatus=db.queryForObject("SELECT status FROM import_job WHERE id=?",String.class,existing);
      if("CONFIRMED".equals(existingStatus))return parsed.preview(existing);
      job=existing;
      db.update("DELETE FROM import_error WHERE import_job_id=?",job);
      db.update("UPDATE import_job SET status=?,original_filename=?,file_size=?,content=?,external_id=?,plan_version=?,expires_at=?,confirmed_at=NULL,plan_id=NULL WHERE id=?",parsed.issues.isEmpty()?"VALID":"INVALID",safeName(file.getOriginalFilename()),file.getSize(),content,parsed.externalId,parsed.version,java.sql.Timestamp.from(Instant.now().plusSeconds(ttlHours*3600)),job);
    }
    for (Issue issue : parsed.issues) db.update(
        "INSERT INTO import_error(id,import_job_id,row_number,column_name,error_code,message,severity) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID(),job,issue.row(),issue.column(),issue.code(),issue.message(),issue.severity());
    audit(user,"IMPORT_PREVIEW","IMPORT_JOB",job,parsed.issues.isEmpty()?"SUCCESS":"REJECTED");
    return parsed.preview(job);
  }

  public Job job(UUID id) {
    UUID user = CurrentUser.id();
    return db
        .query(
            "SELECT id,status,original_filename,checksum,created_at,expires_at,plan_id FROM"
                + " import_job WHERE id=? AND user_id=?",
            (rs, n) ->
                new Job(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getTimestamp(5).toInstant(),
                    rs.getTimestamp(6).toInstant(),
                    rs.getObject(7, UUID.class)),
            id,
            user)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Importación no encontrada"));
  }

  public List<Issue> errors(UUID id) {
    job(id);
    return db.query(
        "SELECT row_number,column_name,error_code,message,severity FROM import_error WHERE"
            + " import_job_id=? ORDER BY row_number",
        (rs, n) ->
            new Issue(
                (Integer) rs.getObject(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5)),
        id);
  }

  public void delete(UUID id) {
    Job job = job(id);
    if ("CONFIRMED".equals(job.status()))
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_CONFIRMED", "Una importación confirmada no se elimina");
    db.update("DELETE FROM import_job WHERE id=? AND user_id=?", id, CurrentUser.id());
  }

  @Transactional
  public Confirmed confirm(UUID jobId) {
    UUID user = CurrentUser.id();
    var rows =
        db.queryForList(
            "SELECT * FROM import_job WHERE id=? AND user_id=? FOR UPDATE", jobId, user);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Importación no encontrada");
    Map<String, Object> job = rows.getFirst();
    if ("CONFIRMED".equals(job.get("status")))
      return new Confirmed(jobId, (UUID) job.get("plan_id"), "CONFIRMED");
    if (!"VALID".equals(job.get("status"))
        || ((java.sql.Timestamp) job.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_NOT_CONFIRMABLE", "La importación no puede confirmarse");
    Parsed parsed;
    try {
      parsed = parse((String) job.get("content"));
    } catch (Exception exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_CHANGED", "La validación ya no es válida");
    }
    if (!parsed.issues.isEmpty())
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_CHANGED", "La validación ya no es válida");
    String accountEmail=db.queryForObject("SELECT email FROM app_user WHERE id=?",String.class,user);
    if(accountEmail==null||!accountEmail.equalsIgnoreCase(parsed.rows.getFirst().userIdentifier))
      throw new ApiException(HttpStatus.BAD_REQUEST,"USER_IDENTIFIER_MISMATCH","El user_identifier del CSV debe ser el email de la cuenta actual");
    Integer conflict =
        db.queryForObject(
            "SELECT count(*) FROM workout_plan WHERE user_id=? AND external_id=? AND version=?",
            Integer.class,
            user,
            parsed.externalId,
            parsed.version);
    if (conflict != null && conflict > 0)
      throw new ApiException(
          HttpStatus.CONFLICT, "PLAN_VERSION_EXISTS", "Ya existe esa versión del plan");
    UUID plan = UUID.randomUUID();
    db.update(
        "UPDATE workout_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='ACTIVE'",
        user);
    db.update(
        "INSERT INTO"
            + " workout_plan(id,user_id,external_id,name,version,status,valid_from,valid_until,activated_at)"
            + " VALUES(?,?,?,?,?,'ACTIVE',?,?,CURRENT_TIMESTAMP)",
        plan,
        user,
        parsed.externalId,
        parsed.name,
        parsed.version,
        parsed.validFrom,
        parsed.validUntil);
    Map<String, UUID> days = new LinkedHashMap<>();
    for (Row row : parsed.rows) {
      String dayKey = row.week + ":" + row.day;
      UUID day = days.get(dayKey);
      if (day == null) {
        day = UUID.randomUUID();
        days.put(dayKey, day);
        db.update(
            "INSERT INTO"
                + " workout_plan_day(id,workout_plan_id,week_number,day_number,day_name,session_name,day_order)"
                + " VALUES(?,?,?,?,?,?,?)",
            day,
            plan,
            row.week,
            row.day,
            row.dayName,
            row.sessionName,
            days.size());
      }
      UUID exercise = db.queryForObject(
          "INSERT INTO exercise(id,code,name,muscle_group,equipment) VALUES(?,?,?,?,?)"
              + " ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,muscle_group=EXCLUDED.muscle_group,equipment=EXCLUDED.equipment,updated_at=CURRENT_TIMESTAMP RETURNING id",
          UUID.class, UUID.randomUUID(), row.exerciseCode, row.exerciseName, row.muscleGroup, row.equipment);
      db.update(
          "INSERT INTO"
              + " planned_exercise(id,workout_plan_day_id,exercise_id,exercise_order,sets,reps_min,reps_max,target_rir,target_rpe,rest_seconds,tempo,warmup_required,superset_group,alternative_exercise_code,instructions,notes)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          day,
          exercise,
          row.order,
          row.sets,
          row.repsMin,
          row.repsMax,
          row.rir,
          row.rpe,
          row.rest,
          row.tempo,
          row.warmup,
          row.superset,
          row.alternative,
          row.instructions,
          row.notes);
    }
    db.update(
        "UPDATE import_job SET status='CONFIRMED',confirmed_at=CURRENT_TIMESTAMP,plan_id=? WHERE"
            + " id=?",
        plan,
        jobId);
    audit(user, "IMPORT_CONFIRM", "WORKOUT_PLAN", plan, "SUCCESS");
    return new Confirmed(jobId, plan, "CONFIRMED");
  }

  private Parsed parse(String content) throws Exception {
    List<Issue> issues = new ArrayList<>();
    List<Row> rows = new ArrayList<>();
    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new StringReader(content))) {
      if (!parser.getHeaderNames().equals(CsvSchemaRegistry.COLUMNS)) {
        issues.add(
            new Issue(
                1,
                "header",
                "INVALID_HEADER",
                "Las columnas no coinciden con entrenamiento_plan_v1",
                "ERROR"));
        return new Parsed(rows, issues, null, null, null, null, null);
      }
      for (CSVRecord r : parser) {
        if (rows.size() >= maxRows) {
          issues.add(new Issue(null, null, "ROW_LIMIT", "Se supera el máximo de filas", "ERROR"));
          break;
        }
        try {
          rows.add(row(r));
        } catch (RowException e) {
          issues.add(
              new Issue(
                  (int) r.getRecordNumber() + 1,
                  e.column,
                  "INVALID_VALUE",
                  e.getMessage(),
                  "ERROR"));
        }
      }
    }
    if (rows.isEmpty())
      issues.add(
          new Issue(null, null, "EMPTY_FILE", "El archivo no contiene filas válidas", "ERROR"));
    if (!rows.isEmpty()) {
      Row first = rows.getFirst();
      if (!"v1".equals(first.schema))
        issues.add(
            new Issue(2, "schema_version", "INVALID_SCHEMA_VERSION", "Debe ser v1", "ERROR"));
      for (Row row : rows) {
        if (!row.externalId.equals(first.externalId)
            || row.version != first.version
            || !row.name.equals(first.name)
            || !row.userIdentifier.equalsIgnoreCase(first.userIdentifier))
          issues.add(
              new Issue(
                  null,
                  null,
                  "MIXED_PLAN",
                  "Todas las filas deben pertenecer al mismo plan y versión",
                  "ERROR"));
      }
      long unique = rows.stream().map(x -> x.week + ":" + x.day + ":" + x.order).distinct().count();
      if (unique != rows.size())
        issues.add(
            new Issue(
                null,
                "exercise_order",
                "DUPLICATE_ORDER",
                "Día y orden de ejercicio repetidos",
                "ERROR"));
      return new Parsed(
          rows,
          issues,
          first.externalId,
          first.name,
          first.version,
          first.validFrom,
          first.validUntil);
    }
    return new Parsed(rows, issues, null, null, null, null, null);
  }

  private Row row(CSVRecord r) {
    String schema = req(r, "schema_version");
    if ("1.0".equals(schema)) schema = "v1";
    String
        external = req(r, "plan_external_id"),
        name = req(r, "plan_name"),
        userIdentifier = req(r, "user_identifier");
    int version = positive(r, "plan_version"),
        week = positive(r, "week_number"),
        day = positive(r, "day_number"),
        order = positive(r, "exercise_order"),
        sets = positive(r, "sets"),
        min = positive(r, "reps_min"),
        max = positive(r, "reps_max");
    if (min > max) throw new RowException("reps_max", "Debe ser mayor o igual que reps_min");
    String bool = req(r, "warmup_required").toLowerCase();
    if (!bool.equals("true") && !bool.equals("false"))
      throw new RowException("warmup_required", "Solo se admite true o false");
    return new Row(
        schema,
        external,
        name,
        userIdentifier,
        version,
        week,
        day,
        opt(r, "day_name"),
        req(r, "session_name"),
        order,
        req(r, "exercise_code"),
        req(r, "exercise_name"),
        opt(r, "muscle_group"),
        opt(r, "equipment"),
        sets,
        min,
        max,
        decimal(r, "target_rir"),
        decimal(r, "target_rpe"),
        integer(r, "rest_seconds"),
        opt(r, "tempo"),
        Boolean.parseBoolean(bool),
        opt(r, "superset_group"),
        opt(r, "alternative_exercise_code"),
        opt(r, "instructions"),
        opt(r, "notes"),
        date(r, "valid_from"),
        date(r, "valid_until"));
  }

  private String req(CSVRecord r, String c) {
    String v = opt(r, c);
    if (v == null) throw new RowException(c, "Campo obligatorio");
    return v;
  }

  private String opt(CSVRecord r, String c) {
    String v = r.get(c).trim();
    return v.isEmpty() ? null : v;
  }

  private int positive(CSVRecord r, String c) {
    try {
      int v = Integer.parseInt(req(r, c));
      if (v <= 0) throw new Exception();
      return v;
    } catch (Exception e) {
      throw new RowException(c, "Debe ser un entero positivo");
    }
  }

  private Integer integer(CSVRecord r, String c) {
    String v = opt(r, c);
    if (v == null) return null;
    try {
      int x = Integer.parseInt(v);
      if (x < 0) throw new Exception();
      return x;
    } catch (Exception e) {
      throw new RowException(c, "Debe ser un entero no negativo");
    }
  }

  private BigDecimal decimal(CSVRecord r, String c) {
    String v = opt(r, c);
    if (v == null) return null;
    try {
      return new BigDecimal(v.replace(',', '.'));
    } catch (Exception e) {
      throw new RowException(c, "Decimal inválido");
    }
  }

  private LocalDate date(CSVRecord r, String c) {
    String v = opt(r, c);
    if (v == null) return null;
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      throw new RowException(c, "Fecha ISO 8601 inválida");
    }
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private String safeName(String name) {
    return name == null ? "training.csv" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void audit(UUID actor, String action, String type, UUID id, String result) {
    db.update(
        "INSERT INTO audit_log(id,actor_id,action,entity_type,entity_id,result)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        actor,
        action,
        type,
        id,
        result);
  }

  private record Row(
      String schema,
      String externalId,
      String name,
      String userIdentifier,
      int version,
      int week,
      int day,
      String dayName,
      String sessionName,
      int order,
      String exerciseCode,
      String exerciseName,
      String muscleGroup,
      String equipment,
      int sets,
      int repsMin,
      int repsMax,
      BigDecimal rir,
      BigDecimal rpe,
      Integer rest,
      String tempo,
      boolean warmup,
      String superset,
      String alternative,
      String instructions,
      String notes,
      LocalDate validFrom,
      LocalDate validUntil) {}

  private record Parsed(
      List<Row> rows,
      List<Issue> issues,
      String externalId,
      String name,
      Integer version,
      LocalDate validFrom,
      LocalDate validUntil) {
    Preview preview(UUID id) {
      return new Preview(
          id,
          issues.isEmpty() ? "VALID" : "INVALID",
          issues.isEmpty(),
          externalId,
          name,
          version,
          rows.stream().mapToInt(Row::week).max().orElse(0),
          (int) rows.stream().map(x -> x.week + ":" + x.day).distinct().count(),
          rows.size(),
          validFrom,
          validUntil,
          issues);
    }
  }

  private static class RowException extends RuntimeException {
    final String column;

    RowException(String column, String message) {
      super(message);
      this.column = column;
    }
  }
}
