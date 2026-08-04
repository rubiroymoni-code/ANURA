package app.anura.progress;

import static org.junit.jupiter.api.Assertions.*;
import app.anura.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BodyProgressServiceTest {
    private final BodyProgressService service=new BodyProgressService(null,null);

    @Test void acceptsReasonableMeasurements(){assertDoesNotThrow(()->service.validate(request("78.4","84")));}
    @Test void rejectsNonPositiveOrExtremeWeight(){assertThrows(ApiException.class,()->service.validate(request("0","84")));assertThrows(ApiException.class,()->service.validate(request("501","84")));}
    @Test void rejectsExtremeMeasurement(){assertThrows(ApiException.class,()->service.validate(request("78","400")));}
    @Test void requiresDateAndWeight(){var r=new BodyProgressService.CheckinRequest(null,null,null,null,null,null,null,null,null,null,null,null,null,null);assertThrows(ApiException.class,()->service.validate(r));}

    private BodyProgressService.CheckinRequest request(String weight,String waist){return new BodyProgressService.CheckinRequest(LocalDate.of(2026,7,26),new BigDecimal(weight),new BigDecimal("18"),new BigDecimal("61.7"),new BigDecimal("7.5"),new BigDecimal("16.1"),new BigDecimal(waist),new BigDecimal("100"),new BigDecimal("95"),new BigDecimal("35"),new BigDecimal("35.5"),new BigDecimal("57"),new BigDecimal("57.5"),"Seguimiento semanal");}
}
