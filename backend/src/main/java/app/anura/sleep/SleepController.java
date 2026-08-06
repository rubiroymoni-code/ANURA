package app.anura.sleep;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sleep")
public class SleepController {
    private static final int GOAL_MINUTES = 480;
    private final JdbcTemplate db;

    SleepController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/today")
    ResponseEntity<Map<String, Object>> today(@RequestParam LocalDate date) {
        return db.queryForList("SELECT * FROM sleep_session WHERE user_id=? AND sleep_date=?", CurrentUser.id(), date)
                .stream().findFirst().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok().build());
    }

    @GetMapping
    List<Map<String, Object>> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw bad("INVALID_RANGE", "El intervalo máximo es de 366 días");
        }
        return db.queryForList("SELECT * FROM sleep_session WHERE user_id=? AND sleep_date BETWEEN ? AND ? ORDER BY sleep_date", CurrentUser.id(), from, to);
    }

    @PostMapping
    Map<String, Object> save(@RequestBody Input input) {
        validate(input);
        UUID user = CurrentUser.id();
        try {
            db.update("INSERT INTO sleep_session(id,user_id,sleep_date,total_sleep_minutes,quality_score,morning_energy,bed_time,wake_time,notes) VALUES(?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT(user_id,sleep_date) DO UPDATE SET total_sleep_minutes=EXCLUDED.total_sleep_minutes,quality_score=EXCLUDED.quality_score,morning_energy=EXCLUDED.morning_energy,bed_time=EXCLUDED.bed_time,wake_time=EXCLUDED.wake_time,notes=EXCLUDED.notes,updated_at=CURRENT_TIMESTAMP",
                    UUID.randomUUID(), user, input.sleepDate(), input.totalSleepMinutes(), input.qualityScore(), input.morningEnergy(), input.bedTime(), input.wakeTime(), input.notes());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("SLEEP_DATE_CONFLICT", "Ya existe un registro para esa fecha");
        }
        return findByDate(user, input.sleepDate());
    }

    @PutMapping("/{id}")
    Map<String, Object> update(@PathVariable UUID id, @RequestBody Input input) {
        Map<String, Object> existing = owned(id);
        validate(input);
        UUID user = CurrentUser.id();
        Integer conflict = db.queryForObject("SELECT COUNT(*) FROM sleep_session WHERE user_id=? AND sleep_date=? AND id<>?", Integer.class, user, input.sleepDate(), id);
        if (conflict != null && conflict > 0) throw conflict("SLEEP_DATE_CONFLICT", "Ya existe un registro para esa fecha");
        try {
            db.update("UPDATE sleep_session SET sleep_date=?,total_sleep_minutes=?,quality_score=?,morning_energy=?,bed_time=?,wake_time=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                    input.sleepDate(), input.totalSleepMinutes(), input.qualityScore(), input.morningEnergy(), input.bedTime(), input.wakeTime(), input.notes(), existing.get("id"), user);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("SLEEP_DATE_CONFLICT", "Ya existe un registro para esa fecha");
        }
        return owned(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { owned(id); db.update("DELETE FROM sleep_session WHERE id=? AND user_id=?", id, CurrentUser.id()); }

    @GetMapping("/summary")
    Map<String, Object> summary(@RequestParam(defaultValue = "30") int days, @RequestParam LocalDate to) {
        int range = Math.min(Math.max(days, 1), 366);
        LocalDate from = to.minusDays(range - 1L);
        List<Map<String, Object>> rows = db.queryForList("SELECT sleep_date,total_sleep_minutes,quality_score,morning_energy FROM sleep_session WHERE user_id=? AND sleep_date BETWEEN ? AND ? ORDER BY sleep_date", CurrentUser.id(), from, to);
        double average = rows.stream().mapToInt(this::minutes).average().orElse(0);
        double quality = rows.stream().filter(row -> row.get("quality_score") != null).mapToInt(row -> ((Number) row.get("quality_score")).intValue()).average().orElse(0);
        double energy = rows.stream().filter(row -> row.get("morning_energy") != null).mapToInt(row -> ((Number) row.get("morning_energy")).intValue()).average().orElse(0);
        int meetingGoal = (int) rows.stream().filter(row -> minutes(row) >= GOAL_MINUTES).count();
        int debt = rows.stream().mapToInt(row -> Math.max(0, GOAL_MINUTES - minutes(row))).sum();
        return Map.of("days", range, "records", rows.size(), "averageSleepMinutes", Math.round(average), "averageQuality", quality, "averageEnergy", energy,
                "goalMinutes", GOAL_MINUTES, "goalCompletionPercentage", rows.isEmpty() ? 0 : Math.round(meetingGoal * 100.0 / rows.size()),
                "sleepDebtMinutes", debt, "currentStreak", streak(rows, to), "series", rows);
    }

    private Map<String, Object> findByDate(UUID user, LocalDate date) { return db.queryForMap("SELECT * FROM sleep_session WHERE user_id=? AND sleep_date=?", user, date); }
    private Map<String, Object> owned(UUID id) { return db.queryForList("SELECT * FROM sleep_session WHERE id=? AND user_id=?", id, CurrentUser.id()).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLEEP_NOT_FOUND", "Registro de sueño no encontrado")); }
    private int minutes(Map<String, Object> row) { return ((Number) row.get("total_sleep_minutes")).intValue(); }
    static int goalPercentage(List<Map<String, Object>> rows) { int count = (int) rows.stream().filter(row -> ((Number) row.get("total_sleep_minutes")).intValue() >= GOAL_MINUTES).count(); return rows.isEmpty() ? 0 : Math.round(count * 100.0f / rows.size()); }
    static int debt(List<Map<String, Object>> rows) { return rows.stream().mapToInt(row -> Math.max(0, GOAL_MINUTES - ((Number) row.get("total_sleep_minutes")).intValue())).sum(); }
    static int streak(List<Map<String, Object>> rows, LocalDate end) { int result = 0; LocalDate expected = end; for (int i = rows.size() - 1; i >= 0; i--) { LocalDate date = ((java.sql.Date) rows.get(i).get("sleep_date")).toLocalDate(); if (!date.equals(expected)) break; result++; expected = expected.minusDays(1); } return result; }
    static void validate(Input input) { if (input == null || input.sleepDate() == null || input.totalSleepMinutes() == null || input.totalSleepMinutes() < 0 || input.totalSleepMinutes() > 1440 || input.qualityScore() != null && (input.qualityScore() < 1 || input.qualityScore() > 5) || input.morningEnergy() != null && (input.morningEnergy() < 1 || input.morningEnergy() > 5)) throw bad("INVALID_SLEEP", "Revisa duración, calidad y energía"); }
    private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    public record Input(LocalDate sleepDate, Integer totalSleepMinutes, Integer qualityScore, Integer morningEnergy, LocalTime bedTime, LocalTime wakeTime, String notes) {}
}
