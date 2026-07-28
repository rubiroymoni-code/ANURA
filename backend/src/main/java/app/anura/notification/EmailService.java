package app.anura.notification;

import app.anura.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final RestClient brevo;
    private final boolean brevoEnabled;
    private final String brevoApiKey;
    private final String brevoFromEmail;
    private final String brevoFromName;
    private final String frontendUrl;

    EmailService(RestClient.Builder builder,
                 @Value("${app.mail.brevo.enabled:false}") boolean brevoEnabled,
                 @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
                 @Value("${app.mail.brevo.from-email:}") String brevoFromEmail,
                 @Value("${app.mail.brevo.from-name:ANURA}") String brevoFromName,
                 @Value("${app.mail.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.brevo = builder.baseUrl("https://api.brevo.com/v3").build();
        this.brevoEnabled = brevoEnabled;
        this.brevoApiKey = brevoApiKey;
        this.brevoFromEmail = brevoFromEmail;
        this.brevoFromName = brevoFromName;
        this.frontendUrl = frontendUrl;
    }

    public boolean enabled() {
        return brevoConfigured();
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public void send(String to, String subject, String body) {
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "El servicio de correo todavia no esta configurado");
        }
        try {
            brevo.post()
                .uri("/smtp/email")
                .header("api-key", brevoApiKey)
                .body(Map.of(
                    "sender", Map.of("name", brevoFromName, "email", brevoFromEmail),
                    "to", new Object[]{Map.of("email", to)},
                    "subject", subject,
                    "textContent", body,
                    "htmlContent", brandedHtml(subject, body)))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            String providerBody = exception.getResponseBodyAsString();
            log.error("Brevo rechazó el correo: status={}, response={}", exception.getStatusCode().value(), providerBody);
            String message = switch (exception.getStatusCode().value()) {
                case 400 -> "Brevo rechazó los datos del correo; revisa el remitente configurado";
                case 401, 403 -> "Brevo rechazó la API key o el remitente no está verificado";
                case 429 -> "Se alcanzó temporalmente el límite de envíos de Brevo";
                default -> "Brevo no pudo entregar el correo";
            };
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", message);
        } catch (Exception exception) {
            log.error("No se pudo conectar con Brevo", exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", "No se pudo conectar con Brevo");
        }
    }

    private boolean brevoConfigured() {
        return brevoEnabled && !brevoApiKey.isBlank() && !brevoFromEmail.isBlank();
    }

    private String brandedHtml(String subject, String body) {
        String safeSubject = escape(subject);
        String safeBody = escape(body).replace("\n", "<br>");
        return """
            <!doctype html><html lang="es"><body style="margin:0;background:#f1f3ed;font-family:Arial,sans-serif;color:#122018">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f3ed;padding:32px 14px"><tr><td align="center">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#fff;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(18,32,24,.12)">
                <tr><td style="background:#13251a;padding:28px 32px;color:#fff">
                  <img src="%s/icon-192.png" width="44" height="44" alt="ANURA" style="display:inline-block;vertical-align:middle;border-radius:12px">
                  <span style="margin-left:10px;font-size:20px;font-weight:800;letter-spacing:3px">ANURA</span>
                </td></tr>
                <tr><td style="padding:34px 32px 18px">
                  <div style="color:#718076;font-size:11px;font-weight:800;letter-spacing:2px;text-transform:uppercase">Tu espacio saludable</div>
                  <h1 style="margin:10px 0 18px;font-size:28px;line-height:1.15">%s</h1>
                  <div style="font-size:16px;line-height:1.75;color:#35443a;background:#f3f6ee;border-left:4px solid #c7f454;border-radius:12px;padding:18px">%s</div>
                  <div style="padding-top:26px"><a href="%s" style="display:inline-block;background:#c7f454;color:#13251a;text-decoration:none;border-radius:14px;padding:14px 24px;font-weight:800">Abrir ANURA</a></div>
                </td></tr>
                <tr><td style="padding:18px 32px 30px;color:#8a958d;font-size:12px;line-height:1.5">Si no has solicitado este mensaje, puedes ignorarlo con seguridad.<br>ANURA · Tu progreso, en un solo lugar.</td></tr>
              </table>
            </td></tr></table></body></html>
            """.formatted(escape(frontendUrl), safeSubject, safeBody, escape(frontendUrl));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

}
