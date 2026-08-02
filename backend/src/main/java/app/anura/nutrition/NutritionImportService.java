package app.anura.nutrition;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.HexFormat;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NutritionImportService {
  private final JdbcTemplate db;
  private final long maxSize;
  private final int maxRows;

  NutritionImportService(
      JdbcTemplate db,
      @Value("${app.imports.max-file-size:1048576}") long maxSize,
      @Value("${app.imports.max-rows:2000}") int maxRows) {
    this.db = db;
    this.maxSize = maxSize;
    this.maxRows = maxRows;
  }

  @Transactional
  public Map<String, Object> preview(String type, MultipartFile file) {
    if (file.isEmpty() || file.getSize() > maxSize)
      throw bad("INVALID_FILE_SIZE", "Archivo vacío o demasiado grande");
    try {
      String content = new String(file.getBytes(), StandardCharsets.UTF_8);
      Parsed p = parse(type, content);
      String hash =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
      List<UUID> previous =
          db.query(
              "SELECT id FROM import_job WHERE user_id=? AND import_type=? AND checksum=? AND expires_at>CURRENT_TIMESTAMP ORDER BY"
                  + " created_at DESC",
              (rs, n) -> rs.getObject(1, UUID.class),
              CurrentUser.id(),
              type,
              hash);
      if (!previous.isEmpty()) return p.view(previous.getFirst());
      UUID id = UUID.randomUUID();
      int inserted=db.update(
          "INSERT INTO"
              + " import_job(id,user_id,import_type,schema_version,status,original_filename,checksum,file_size,content,external_id,plan_version,expires_at,import_scope)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
          id,
          CurrentUser.id(),
          type,
          "v1",
          p.errors.isEmpty() ? "VALID" : "INVALID",
          safe(file.getOriginalFilename()),
          hash,
          file.getSize(),
          content,
          p.external,
          p.version,
          java.sql.Timestamp.from(Instant.now().plusSeconds(86400)),
          p.scope);
      if(inserted==0){
        UUID existing=db.query("SELECT id FROM import_job WHERE user_id=? AND import_type=? AND checksum=? ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getObject(1,UUID.class),CurrentUser.id(),type,hash).stream().findFirst().orElseThrow(()->bad("IMPORT_CONFLICT","La previsualización ya se está procesando"));
        return p.view(existing);
      }
      for (Issue e : p.errors)
        db.update(
            "INSERT INTO"
                + " import_error(id,import_job_id,row_number,column_name,error_code,message,severity)"
                + " VALUES(?,?,?,?,?,?,'ERROR')",
            UUID.randomUUID(),
            id,
            e.row,
            e.column,
            e.code,
            e.message);
      audit(
          "NUTRITION_IMPORT_PREVIEW",
          "IMPORT_JOB",
          id,
          p.errors.isEmpty() ? "SUCCESS" : "REJECTED");
      return p.view(id);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw bad("IMPORT_PREVIEW_FAILED", "No se pudo preparar la previsualización del CSV");
    }
  }

  @Transactional
  public Map<String, Object> confirm(UUID id) {
    UUID user = CurrentUser.id();
    Map<String, Object> job =
        db
            .queryForList("SELECT * FROM import_job WHERE id=? AND user_id=? FOR UPDATE", id, user)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Importación no encontrada"));
    if ("CONFIRMED".equals(job.get("status")))
      return Map.of("importJobId", id, "status", "CONFIRMED");
    if (!"VALID".equals(job.get("status")))
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_INVALID", "La importación contiene errores");
    Parsed p = parseUnchecked((String) job.get("import_type"), (String) job.get("content"));
    if (!p.errors.isEmpty())
      throw new ApiException(
          HttpStatus.CONFLICT, "IMPORT_CHANGED", "La importación dejó de ser válida");
    if ("RECIPES".equals(job.get("import_type"))) persistRecipes(p, user, null);
    else persistPlan(p, user);
    db.update(
        "UPDATE import_job SET status='CONFIRMED',confirmed_at=CURRENT_TIMESTAMP WHERE id=?", id);
    audit("NUTRITION_IMPORT_CONFIRM", "IMPORT_JOB", id, "SUCCESS");
    return Map.of("importJobId", id, "status", "CONFIRMED");
  }

  private void persistPlan(Parsed p, UUID user) {
    if ("INDIVIDUAL_DIET".equals(p.type)) {
      String email = db.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, user);
      if (email == null || p.scope == null || !email.equalsIgnoreCase(p.scope))
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "USER_IDENTIFIER_MISMATCH",
            "El usuario del CSV no coincide con la sesión");
    }
    UUID household = null;
    if ("SHARED_DIET".equals(p.type)) {
      household =
          db
              .query(
                  "SELECT h.id FROM household h JOIN household_member m ON m.household_id=h.id"
                      + " WHERE m.user_id=? AND (lower(h.name)=lower(?) OR CAST(h.id AS VARCHAR)=?)",
                  (r, n) -> r.getObject(1, UUID.class),
                  user,
                  p.scope,
                  p.scope)
              .stream()
              .findFirst()
              .orElseThrow(() -> bad("HOUSEHOLD_NOT_FOUND", "Unidad doméstica no encontrada"));
    }
    Integer exists =
        db.queryForObject(
            "SELECT count(*) FROM nutrition_plan WHERE "
                + (household == null ? "owner_id=?" : "household_id=?")
                + " AND external_id=? AND version=?",
            Integer.class,
            household == null ? user : household,
            p.external,
            p.version);
    if (exists != null && exists > 0)
      throw new ApiException(HttpStatus.CONFLICT, "PLAN_VERSION_EXISTS", "Ya existe esa versión");
    UUID plan = UUID.randomUUID();
    if (household == null)
      db.update(
          "INSERT INTO"
              + " nutrition_plan(id,owner_id,external_id,name,version,status,valid_from,valid_until)"
              + " VALUES(?,?,?,?,?,'DRAFT',?,?)",
          plan,
          user,
          p.external,
          p.name,
          p.version,
          p.from,
          p.until);
    else
      db.update(
          "INSERT INTO"
              + " nutrition_plan(id,household_id,external_id,name,version,status,valid_from,valid_until)"
              + " VALUES(?,?,?,?,?,'DRAFT',?,?)",
          plan,
          household,
          p.external,
          p.name,
          p.version,
          p.from,
          p.until);
    final UUID householdScope = household;
    persistRecipes(p, user, householdScope);
    Map<String, UUID> days = new LinkedHashMap<>(), meals = new LinkedHashMap<>();
    for (Row r : p.rows) {
      String dk = r.week + ":" + r.day;
      UUID day =
          days.computeIfAbsent(
              dk,
              k -> {
                UUID x = UUID.randomUUID();
                db.update(
                    "INSERT INTO"
                        + " nutrition_plan_day(id,nutrition_plan_id,week_number,day_number,day_name,day_order)"
                        + " VALUES(?,?,?,?,?,?)",
                    x,
                    plan,
                    r.week,
                    r.day,
                    r.dayName,
                    days.size() + 1);
                return x;
              });
      String mk = dk + ":" + r.mealOrder;
      UUID meal =
          meals.computeIfAbsent(
              mk,
              k -> {
                UUID recipe = recipe(r.recipeCode, r.recipeName, user, householdScope);
                UUID x = UUID.randomUUID();
                db.update(
                    "INSERT INTO"
                        + " planned_meal(id,nutrition_plan_day_id,recipe_id,meal_type,meal_name,meal_order)"
                        + " VALUES(?,?,?,?,?,?)",
                    x,
                    day,
                    recipe,
                    r.mealType,
                    r.mealName,
                    r.mealOrder);
                return x;
              });
      for (var portion : r.portions.entrySet()) {
        UUID uid = userByEmail(portion.getKey(), householdScope, user);
        var factor =
            r.quantity.divide(java.math.BigDecimal.valueOf(100)).multiply(portion.getValue());
        db.update(
            "INSERT INTO"
                + " user_meal_portion(id,planned_meal_id,user_id,portion_multiplier,calories,protein,carbohydrates,fat)"
                + " VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(planned_meal_id,user_id) DO UPDATE SET"
                + " calories=user_meal_portion.calories+EXCLUDED.calories,"
                + " protein=user_meal_portion.protein+EXCLUDED.protein,"
                + " carbohydrates=user_meal_portion.carbohydrates+EXCLUDED.carbohydrates,"
                + " fat=user_meal_portion.fat+EXCLUDED.fat",
            UUID.randomUUID(),
            meal,
            uid,
            portion.getValue(),
            r.calories.multiply(factor),
            r.protein.multiply(factor),
            r.carbs.multiply(factor),
            r.fat.multiply(factor));
      }
    }
  }

  private void persistRecipes(Parsed p, UUID user, UUID household) {
    for (Row r : p.rows) {
      UUID recipe = recipe(r.recipeCode, r.recipeName, user, household);
      UUID ing =
          db
              .query(
                  "SELECT id FROM ingredient WHERE code=? AND (owner_id=? OR household_id=?)",
                  (x, n) -> x.getObject(1, UUID.class),
                  r.ingredientCode,
                  user,
                  household)
              .stream()
              .findFirst()
              .orElseGet(
                  () -> {
                    UUID x = UUID.randomUUID();
                    db.update(
                        "INSERT INTO"
                            + " ingredient(id,household_id,owner_id,code,name,category,base_unit,calories_100,protein_100,carbohydrates_100,fat_100,fiber_100)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        x,
                        household,
                        household == null ? user : null,
                        r.ingredientCode,
                        r.ingredientName,
                        r.category,
                        r.unit,
                        r.calories,
                        r.protein,
                        r.carbs,
                        r.fat,
                        r.fiber);
                    return x;
                  });
      Integer n =
          db.queryForObject(
              "SELECT count(*) FROM recipe_ingredient WHERE recipe_id=? AND ingredient_id=?",
              Integer.class,
              recipe,
              ing);
      if (n == 0)
        db.update(
            "INSERT INTO"
                + " recipe_ingredient(id,recipe_id,ingredient_id,quantity,unit,ingredient_order)"
                + " VALUES(?,?,?,?,?,?)",
            UUID.randomUUID(),
            recipe,
            ing,
            r.quantity,
            r.unit,
            r.ingredientOrder);
    }
  }

  private UUID recipe(String code, String name, UUID user, UUID household) {
    return db
        .query(
            "SELECT id FROM recipe WHERE code=? AND (owner_id=? OR household_id=?)",
            (r, n) -> r.getObject(1, UUID.class),
            code,
            user,
            household)
        .stream()
        .findFirst()
        .orElseGet(
            () -> {
              UUID id = UUID.randomUUID();
              db.update(
                  "INSERT INTO recipe(id,household_id,owner_id,code,name,servings)"
                      + " VALUES(?,?,?,?,?,1)",
                  id,
                  household,
                  household == null ? user : null,
                  code,
                  name);
              return id;
            });
  }

  private UUID userByEmail(String email, UUID household, UUID owner) {
    if (household == null) return owner;
    return db
        .query(
            "SELECT u.id FROM app_user u JOIN household_member m ON m.user_id=u.id WHERE"
                + " m.household_id=? AND lower(u.email)=lower(?)",
            (r, n) -> r.getObject(1, UUID.class),
            household,
            email)
        .stream()
        .findFirst()
        .orElseThrow(() -> bad("MEMBER_NOT_FOUND", "Miembro no encontrado: " + email));
  }

  private Parsed parseUnchecked(String t, String c) {
    try {
      return parse(t, c);
    } catch (Exception e) {
      throw bad("INVALID_CSV", "CSV inválido");
    }
  }

  private Parsed parse(String type, String content) throws Exception {
    List<Row> rows = new ArrayList<>();
    List<Issue> errors = new ArrayList<>();
    try (CSVParser p =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new StringReader(content))) {
      for (CSVRecord c : p) {
        if (rows.size() >= maxRows) {
          errors.add(new Issue(null, null, "ROW_LIMIT", "Demasiadas filas"));
          break;
        }
        try {
          rows.add(Row.from(type, c, (int) c.getRecordNumber() + 1));
        } catch (IllegalArgumentException e) {
          errors.add(
              new Issue((int) c.getRecordNumber() + 1, "row", "INVALID_VALUE", e.getMessage()));
        }
      }
    }
    if (rows.isEmpty()) errors.add(new Issue(null, null, "EMPTY", "Sin filas"));
    Row f = rows.isEmpty() ? null : rows.getFirst();
    return new Parsed(
        type,
        rows,
        errors,
        f == null ? null : f.external,
        f == null ? null : f.planName,
        f == null ? null : f.version,
        f == null ? null : f.scope,
        f == null ? null : f.from,
        f == null ? null : f.until);
  }

  private void audit(String action, String type, UUID id, String result) {
    db.update(
        "INSERT INTO audit_log(id,actor_id,action,entity_type,entity_id,result)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        CurrentUser.id(),
        action,
        type,
        id,
        result);
  }

  private String safe(String s) {
    return s == null ? "nutrition.csv" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private ApiException bad(String c, String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, c, m);
  }

  record Issue(Integer row, String column, String code, String message) {}

  record Parsed(
      String type,
      List<Row> rows,
      List<Issue> errors,
      String external,
      String name,
      Integer version,
      String scope,
      LocalDate from,
      LocalDate until) {
    Map<String, Object> view(UUID id) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("importJobId", id);
      m.put("status", errors.isEmpty() ? "VALID" : "INVALID");
      m.put("confirmable", errors.isEmpty());
      m.put("planName", name);
      m.put("version", version);
      m.put("rows", rows.size());
      m.put("recipes", rows.stream().map(Row::recipeCode).distinct().count());
      m.put("ingredients", rows.stream().map(Row::ingredientCode).distinct().count());
      m.put("users", rows.stream().flatMap(x -> x.portions.keySet().stream()).distinct().toList());
      m.put("issues", errors);
      return m;
    }
  }

  record Row(
      String external,
      String planName,
      int version,
      String scope,
      int week,
      int day,
      String dayName,
      int mealOrder,
      String mealType,
      String mealName,
      String recipeCode,
      String recipeName,
      String ingredientCode,
      String ingredientName,
      String category,
      java.math.BigDecimal quantity,
      String unit,
      java.math.BigDecimal calories,
      java.math.BigDecimal protein,
      java.math.BigDecimal carbs,
      java.math.BigDecimal fat,
      java.math.BigDecimal fiber,
      int ingredientOrder,
      Map<String, java.math.BigDecimal> portions,
      LocalDate from,
      LocalDate until) {
    static Row from(String type, CSVRecord r, int order) {
      String external = "RECIPES".equals(type) ? "recipes" : req(r, "plan_external_id"),
          name = "RECIPES".equals(type) ? "Recetas" : req(r, "plan_name");
      int version = "RECIPES".equals(type) ? 1 : pos(r, "plan_version");
      String scope =
          "SHARED_DIET".equals(type) ? req(r, "household_identifier") : opt(r, "user_identifier");
      Map<String, java.math.BigDecimal> portions = new LinkedHashMap<>();
      if ("SHARED_DIET".equals(type)) {
        portions.put(req(r, "user_1_identifier"), dec(r, "user_1_portion_multiplier"));
        portions.put(req(r, "user_2_identifier"), dec(r, "user_2_portion_multiplier"));
      } else if (!"RECIPES".equals(type))
        portions.put(scope, decDefault(r, "portion_multiplier", java.math.BigDecimal.ONE));
      return new Row(
          external,
          name,
          version,
          scope,
          "RECIPES".equals(type) ? 1 : pos(r, "week_number"),
          "RECIPES".equals(type) ? 1 : pos(r, "day_number"),
          opt(r, "day_name"),
          "RECIPES".equals(type) ? 1 : pos(r, "meal_order"),
          opt(r, "meal_type"),
          opt(r, "meal_name"),
          req(r, "recipe_code"),
          req(r, "recipe_name"),
          req(r, "ingredient_code"),
          req(r, "ingredient_name"),
          opt(r, "category"),
          dec(
              r,
              "RECIPES".equals(type) || "INDIVIDUAL_DIET".equals(type)
                  ? "quantity"
                  : "quantity_total"),
          req(r, "unit"),
          dec(r, "calories_100"),
          dec(r, "protein_100"),
          dec(r, "carbohydrates_100"),
          dec(r, "fat_100"),
          decDefault(r, "fiber_100", java.math.BigDecimal.ZERO),
          order,
          portions,
          date(r, "valid_from"),
          date(r, "valid_until"));
    }

    static String req(CSVRecord r, String c) {
      String v = opt(r, c);
      if (v == null) throw new IllegalArgumentException(c + " obligatorio");
      return v;
    }

    static String opt(CSVRecord r, String c) {
      if (!r.isMapped(c)) return null;
      String v = r.get(c).trim();
      return v.isEmpty() ? null : v;
    }

    static int pos(CSVRecord r, String c) {
      int v = Integer.parseInt(req(r, c));
      if (v < 1) throw new IllegalArgumentException(c + " debe ser positivo");
      return v;
    }

    static java.math.BigDecimal dec(CSVRecord r, String c) {
      return new java.math.BigDecimal(req(r, c).replace(',', '.'));
    }

    static java.math.BigDecimal decDefault(CSVRecord r, String c, java.math.BigDecimal d) {
      String v = opt(r, c);
      return v == null ? d : new java.math.BigDecimal(v.replace(',', '.'));
    }

    static LocalDate date(CSVRecord r, String c) {
      String v = opt(r, c);
      return v == null ? null : LocalDate.parse(v);
    }
  }
}
