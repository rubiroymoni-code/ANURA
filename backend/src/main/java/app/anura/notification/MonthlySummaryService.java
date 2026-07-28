package app.anura.notification;

import app.anura.error.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MonthlySummaryService {
  private final JdbcTemplate db;
  private final EmailService emails;
  public MonthlySummaryService(JdbcTemplate db,EmailService emails){this.db=db;this.emails=emails;}

  @Scheduled(cron="0 15 9 * * *",zone="Europe/Madrid")
  void due(){for(Map<String,Object> row:db.queryForList("SELECT u.id FROM app_user u LEFT JOIN user_preference p ON p.user_id=u.id WHERE u.enabled=TRUE AND COALESCE(p.reminder_email_enabled,TRUE)=TRUE AND COALESCE(p.last_summary_sent_at,u.created_at)<CURRENT_TIMESTAMP-INTERVAL '1 month'")){try{send((UUID)row.get("id"),true);}catch(Exception ignored){}}}

  public void send(UUID user,boolean scheduled){
    if(!emails.enabled())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"EMAIL_DISABLED","El envío de correo no está configurado");
    Map<String,Object> account=db.queryForMap("SELECT email,display_name FROM app_user WHERE id=?",user);
    Map<String,Object> body=db.queryForMap("SELECT COUNT(*) checkins,COALESCE(MAX(weight)-MIN(weight),0) weight_change FROM body_checkin WHERE user_id=? AND checkin_date>=CURRENT_DATE-INTERVAL '30 days'",user);
    Integer workouts=db.queryForObject("SELECT COUNT(*) FROM workout_session WHERE user_id=? AND status='COMPLETED' AND planned_date>=CURRENT_DATE-INTERVAL '30 days'",Integer.class,user);
    Map<String,Object> meals=db.queryForMap("SELECT COUNT(*) meals,COALESCE(SUM(calories),0) calories FROM consumed_meal WHERE user_id=? AND status IN ('COMPLETED','SUBSTITUTED') AND meal_date>=CURRENT_DATE-INTERVAL '30 days'",user);
    String message="Hola "+account.get("display_name")+",\n\nTu resumen de los últimos 30 días:\n\n· Entrenamientos completados: "+workouts+"\n· Comidas registradas: "+meals.get("meals")+"\n· Calorías registradas: "+meals.get("calories")+" kcal\n· Check-ins corporales: "+body.get("checkins")+"\n· Variación de peso: "+body.get("weight_change")+" kg\n\nAbre ANURA para revisar tu evolución y generar un informe para ChatGPT.";
    emails.send(account.get("email").toString(),scheduled?"Tu resumen mensual de ANURA":"Prueba de resumen ANURA",message);
    db.update("INSERT INTO user_preference(user_id,last_summary_sent_at) VALUES(?,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET last_summary_sent_at=CURRENT_TIMESTAMP",user);
  }
}
