package app.anura.notification;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NutritionPlanExpiryEmailService {
  private final JdbcTemplate db;
  private final EmailService emails;

  NutritionPlanExpiryEmailService(JdbcTemplate db,EmailService emails){this.db=db;this.emails=emails;}

  @Scheduled(cron="0 30 9 * * *",zone="Europe/Madrid")
  void notifyPlansEnding(){
    if(!emails.enabled())return;
    String sql="""
      SELECT DISTINCT p.id plan_id,p.name,p.valid_until,u.id user_id,u.email,u.display_name
      FROM nutrition_plan p
      JOIN app_user u ON u.enabled=TRUE AND (u.id=p.owner_id OR EXISTS(
        SELECT 1 FROM household_member hm WHERE hm.household_id=p.household_id AND hm.user_id=u.id))
      LEFT JOIN user_preference pref ON pref.user_id=u.id
      LEFT JOIN user_reminder_settings reminder_settings ON reminder_settings.user_id=u.id
      LEFT JOIN nutrition_plan_expiry_notice notice ON notice.plan_id=p.id AND notice.user_id=u.id
      WHERE p.status='ACTIVE' AND p.valid_until IS NOT NULL
        AND p.valid_until<=CURRENT_DATE+1 AND notice.plan_id IS NULL
        AND NOT EXISTS(
          SELECT 1 FROM nutrition_plan next
          WHERE next.id<>p.id AND next.status IN ('DRAFT','ACTIVE')
            AND next.valid_from IS NOT NULL AND next.valid_from<=p.valid_until+1
            AND (next.valid_until IS NULL OR next.valid_until>=p.valid_until+1)
            AND ((p.household_id IS NOT NULL AND next.household_id=p.household_id)
              OR (p.household_id IS NULL AND next.owner_id=p.owner_id))
        )
        AND COALESCE(pref.reminder_email_enabled,TRUE)=TRUE
        AND COALESCE(reminder_settings.nutrition_plan_email,TRUE)=TRUE
      """;
    for(Map<String,Object> row:db.queryForList(sql)){
      UUID plan=(UUID)row.get("plan_id"),user=(UUID)row.get("user_id");
      Object rawEnd=row.get("valid_until");
      LocalDate end=rawEnd instanceof LocalDate date?date:((java.sql.Date)rawEnd).toLocalDate();
      long days=ChronoUnit.DAYS.between(LocalDate.now(),end);
      String timing=days<0?"ya ha terminado":days==0?"termina hoy":days==1?"termina mañana":"termina en "+days+" días";
      String message="Hola "+row.get("display_name")+",\n\nTu plan nutricional \""+row.get("name")+"\" "+timing+".\n\nEntra en Evolución, genera el prompt completo con tu progreso y úsalo para preparar la siguiente dieta. Después importa la nueva versión en Nutrición para no perder la planificación diaria ni la lista de compra.\n\nTu histórico seguirá guardado.";
      try{emails.send(row.get("email").toString(),"Tu plan de ANURA necesita una nueva versión",message);db.update("INSERT INTO nutrition_plan_expiry_notice(plan_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",plan,user);}catch(Exception ignored){}
    }
  }
}
