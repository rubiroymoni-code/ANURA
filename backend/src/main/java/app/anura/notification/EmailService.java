package app.anura.notification;

import app.anura.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
 private final JavaMailSender sender;private final boolean enabled;private final String from;private final String frontendUrl;
 EmailService(JavaMailSender sender,@Value("${app.mail.enabled:false}") boolean enabled,@Value("${app.mail.from:no-reply@anura.app}") String from,@Value("${app.mail.frontend-url:http://localhost:5173}") String frontendUrl){this.sender=sender;this.enabled=enabled;this.from=from;this.frontendUrl=frontendUrl;}
 public boolean enabled(){return enabled;}
 public String frontendUrl(){return frontendUrl;}
 public void send(String to,String subject,String body){if(!enabled)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"EMAIL_NOT_CONFIGURED","El servicio de correo todavía no está configurado");SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);message.setSubject(subject);message.setText(body);sender.send(message);}
}
