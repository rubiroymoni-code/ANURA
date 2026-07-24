package app.anura.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException exception) {
        return response(exception.status, exception.code, exception.getMessage(), List.of(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Revisa los datos enviados", violations, null);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> database(DataAccessException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Servicio temporalmente no disponible", List.of(), exception);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "No se pudo completar la operación", List.of(), exception);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
            List<ApiError.FieldViolation> violations, Exception cause) {
        String correlationId = UUID.randomUUID().toString();
        if (cause != null) log.error("Request failed correlationId={} code={}", correlationId, code, cause);
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, correlationId, violations));
    }
}
