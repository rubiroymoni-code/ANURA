package app.anura.workout;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WorkoutExecutionServiceTest {
    private final WorkoutExecutionService service = new WorkoutExecutionService(null, 100, 12);

    @Test void calculatesEpleyEstimate() {
        assertEquals(new BigDecimal("116.67"), service.estimatedOneRepMax(new BigDecimal("100"), 5));
    }

    @Test void rejectsMeaninglessEstimateRanges() {
        assertNull(service.estimatedOneRepMax(new BigDecimal("100"), 0));
        assertNull(service.estimatedOneRepMax(new BigDecimal("100"), 13));
    }
}
