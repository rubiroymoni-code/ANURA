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
    private final RestClient brevo;
    private final boolean resendEnabled;
    private final String resendApiKey;
    private final String resendFrom;
    private final boolean brevoEnabled;
    private final String brevoApiKey;
    private final String brevoFromEmail;
    private final String brevoFromName;
    private final String frontendUrl;

    EmailService(RestClient.Builder builder,
                 @Value("${app.mail.enabled:false}") boolean enabled,
                 @Value("${app.mail.api-key:}") String apiKey,
                 @Value("${app.mail.from:onboarding@resend.dev}") String from,
                 @Value("${app.mail.brevo.enabled:false}") boolean brevoEnabled,
                 @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
                 @Value("${app.mail.brevo.from-email:}") String brevoFromEmail,
                 @Value("${app.mail.brevo.from-name:ANURA}") String brevoFromName,
                 @Value("${app.mail.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.resend = builder.clone().baseUrl("https://api.resend.com").build();
        this.brevo = builder.clone().baseUrl("https://api.brevo.com/v3").build();
        this.resendEnabled = enabled;
        this.resendApiKey = apiKey;
        this.resendFrom = from;
        this.brevoEnabled = brevoEnabled;
        this.brevoApiKey = brevoApiKey;
        this.brevoFromEmail = brevoFromEmail;
        this.brevoFromName = brevoFromName;
        this.frontendUrl = frontendUrl;
    }

    public boolean enabled() {
        return brevoConfigured() || resendConfigured();
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public void send(String to, String subject, String body) {
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "El servicio de correo todavia no esta configurado");
        }
        try {
            if (brevoConfigured()) {
                brevo.post()
                    .uri("/smtp/email")
                    .header("api-key", brevoApiKey)
                    .body(Map.of(
                        "sender", Map.of("name", brevoFromName, "email", brevoFromEmail),
                        "to", new Object[]{Map.of("email", to)},
                        "subject", subject,
                        "textContent", body))
                    .retrieve()
                    .toBodilessEntity();
                return;
            }
            resend.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + resendApiKey)
                .body(Map.of("from", resendFrom, "to", new String[]{to}, "subject", subject, "text", body))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", "No se pudo enviar el correo");
        }
    }

    private boolean brevoConfigured() {
        return brevoEnabled && !brevoApiKey.isBlank() && !brevoFromEmail.isBlank();
    }

    private boolean resendConfigured() {
        return resendEnabled && !resendApiKey.isBlank();
    }
}
