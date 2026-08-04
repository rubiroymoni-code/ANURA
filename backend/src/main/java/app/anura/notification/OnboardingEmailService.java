package app.anura.notification;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OnboardingEmailService {
  private static final Logger log=LoggerFactory.getLogger(OnboardingEmailService.class);
  private final JdbcTemplate db;
  private final EmailService emails;

  public OnboardingEmailService(JdbcTemplate db,EmailService emails){this.db=db;this.emails=emails;}

  public void sendIfReady(UUID user){
    try{
      if(!emails.enabled()||Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM user_onboarding_email WHERE user_id=?)",Boolean.class,user)))return;
      Boolean training=db.queryForObject("SELECT EXISTS(SELECT 1 FROM workout_plan WHERE user_id=?)",Boolean.class,user);
      Boolean nutrition=db.queryForObject("SELECT EXISTS(SELECT 1 FROM nutrition_plan p LEFT JOIN household_member hm ON hm.household_id=p.household_id AND hm.user_id=? WHERE p.owner_id=? OR hm.user_id=?)",Boolean.class,user,user,user);
      if(!Boolean.TRUE.equals(training)||!Boolean.TRUE.equals(nutrition))return;
      Map<String,Object> account=db.queryForMap("SELECT email,display_name FROM app_user WHERE id=?",user);
      String name=String.valueOf(account.getOrDefault("display_name",""));
      emails.send(String.valueOf(account.get("email")),"Ya tienes ANURA preparada: guía para empezar",message(name));
      db.update("INSERT INTO user_onboarding_email(user_id) VALUES(?) ON CONFLICT(user_id) DO NOTHING",user);
    }catch(Exception exception){log.warn("No se pudo enviar la guía inicial de ANURA al usuario {}",user,exception);}
  }

  public void sendGuide(UUID user){
    Map<String,Object> account=db.queryForMap("SELECT email,display_name FROM app_user WHERE id=?",user);
    String name=String.valueOf(account.getOrDefault("display_name",""));
    emails.send(String.valueOf(account.get("email")),"Cómo utilizar ANURA: guía completa",message(name));
  }

  private String message(String name){return """
      Hola %s:

      Ya has importado entrenamiento y nutrición. ANURA está lista para acompañarte cada día.

      1. INICIO
      Aquí verás lo importante de hoy: entrenamiento pendiente, comidas, calorías y progreso diario. Usa los checks para completar rápidamente lo que hayas seguido tal como estaba previsto.

      2. ENTRENAMIENTO
      Abre Entreno y pulsa Empezar. Registra peso, repeticiones y RIR por serie. Puedes pausar, sustituir ejercicios, anotar molestias y finalizar con tus sensaciones. En Histórico podrás revisar cargas, volumen y sesiones anteriores.

      3. NUTRICIÓN
      En Hoy aparecen únicamente las comidas asignadas. Abre una para consultar la receta. En Cocina verás cantidades por persona y el total conjunto; en Plan puedes revisar cualquier día. Si cambias una comida, regístrala para que el seguimiento sea real.

      4. LISTA DE LA COMPRA
      Genérala desde el plan activo. Indica cuánto compras realmente antes de marcar el check. Si compras más de lo necesario, ANURA guarda el sobrante en la despensa doméstica y lo descuenta de las siguientes semanas.

      5. PREFERENCIAS Y HOGAR
      Añade gustos, exclusiones, café y bebidas, alimentos disponibles y notas de cocina. Si compartes unidad doméstica, cada persona mantiene objetivos y cantidades propios, pero comparte cocina y compra.

      6. EVOLUCIÓN
      Haz un check-in periódico de peso, grasa, medidas y fotos. Aquí se concentra la adherencia real de dieta y entrenamiento. No necesitas rellenarlo todo cada vez: la constancia importa más que la perfección.

      7. TRABAJAR CON CHATGPT
      Desde Evolución genera el prompt completo de ANURA. Incluye perfil, objetivos, planes, resultados reales, horarios, preferencias y evolución. Pégalo en ChatGPT junto con las plantillas CSV para crear el siguiente plan compatible e impórtalo después en ANURA.

      Consejo: abre ANURA al comenzar el día y al terminar cada comida o entrenamiento. Son pocos segundos y hacen que tus próximos ajustes se basen en datos reales.
      """.formatted(name.isBlank()?"":name);}
}
