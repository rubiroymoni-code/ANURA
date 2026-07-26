package app.anura.notification;

import app.anura.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EmailService {
    private final RestClient resend;
    private final boolean enabled;
    private final String apiKey;
    private final String from;
    private final String frontendUrl;

    EmailService(RestClient.Builder builder,
                 @Value("${app.mail.enabled:false}") boolean enabled,
                 @Value("${app.mail.api-key:}") String apiKey,
                 @Value("${app.mail.from:onboarding@resend.dev}") String from,
                 @Value("${app.mail.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.resend = builder.baseUrl("https://api.resend.com").build();
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    public boolean enabled() {
        return enabled;
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public void send(String to, String subject, String body) {
        if (!enabled || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "El servicio de correo todavia no esta configurado");
        }
        try {
            resend.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("from", from, "to", new String[]{to}, "subject", subject, "text", body))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", "No se pudo enviar el correo");
        }
    }
}
