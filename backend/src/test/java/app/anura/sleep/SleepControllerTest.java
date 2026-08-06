package app.anura.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SleepControllerTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }
    private Map<String, Object> night(String date, int minutes) { return Map.of("sleep_date", Date.valueOf(date), "total_sleep_minutes", minutes); }

    @Test void calculatesGoalPercentageFromIndividualNights() { assertEquals(50, SleepController.goalPercentage(List.of(night("2026-08-04", 480), night("2026-08-05", 300)))); }
    @Test void calculatesDebt() { assertEquals(180, SleepController.debt(List.of(night("2026-08-05", 300), night("2026-08-04", 480)))); }
    @Test void calculatesCurrentStreakAgainstRequestedDate() { assertEquals(2, SleepController.streak(List.of(night("2026-08-04", 480), night("2026-08-05", 300)), LocalDate.of(2026, 8, 5))); }
    @Test void validatesDurationQualityAndEnergy() { assertThrows(ApiException.class, () -> SleepController.validate(new SleepController.Input(LocalDate.now(), 1441, 3, 3, null, null, null))); assertThrows(ApiException.class, () -> SleepController.validate(new SleepController.Input(LocalDate.now(), 480, 0, 3, null, null, null))); assertThrows(ApiException.class, () -> SleepController.validate(new SleepController.Input(LocalDate.now(), 480, 3, 6, null, null, null))); }

    @Test void postIsIdempotentAndKeepsUserIsolation() {
        UUID user = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.toString(), ""));
        JdbcTemplate db = mock(JdbcTemplate.class);
        Map<String, Object> result = night("2026-08-05", 480);
        when(db.queryForMap(anyString(), any(), any())).thenReturn(result);
        SleepController controller = new SleepController(db);
        SleepController.Input input = new SleepController.Input(LocalDate.of(2026, 8, 5), 480, 4, 3, null, null, null);
        assertEquals(result, controller.save(input));
        assertEquals(result, controller.save(input));
        org.mockito.Mockito.verify(db, org.mockito.Mockito.times(2)).update(anyString(), any(), eq(user), eq(input.sleepDate()), eq(input.totalSleepMinutes()), eq(input.qualityScore()), eq(input.morningEnergy()), eq(input.bedTime()), eq(input.wakeTime()), eq(input.notes()));
    }

    @Test void missingRecordIs404() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), ""));
        JdbcTemplate db = mock(JdbcTemplate.class);
        doReturn(List.of()).when(db).queryForList(anyString(), any(Object[].class));
        assertEquals(404, assertThrows(ApiException.class, () -> new SleepController(db).delete(UUID.randomUUID())).status.value());
    }

    @Test void changingToAnExistingDateIs409() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), ""));
        JdbcTemplate db = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        doReturn(List.of(night("2026-08-04", 480))).when(db).queryForList(anyString(), any(Object[].class));
        when(db.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        SleepController.Input input = new SleepController.Input(LocalDate.of(2026, 8, 5), 480, 4, 3, null, null, null);
        assertEquals(409, assertThrows(ApiException.class, () -> new SleepController(db).update(id, input)).status.value());
    }
}
