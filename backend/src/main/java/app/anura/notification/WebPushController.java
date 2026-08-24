package app.anura.notification;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reminders/push")
public class WebPushController {
  private final JdbcTemplate db;private final WebPushService push;WebPushController(JdbcTemplate db,WebPushService push){this.db=db;this.push=push;}
  @GetMapping("/config") Map<String,Object> config(){Integer devices=db.queryForObject("SELECT COUNT(*) FROM web_push_subscription WHERE user_id=? AND enabled=TRUE",Integer.class,CurrentUser.id());return Map.of("enabled",push.enabled(),"publicKey",push.publicKey(),"devices",devices==null?0:devices);}
  @PostMapping("/subscriptions") Map<String,Object> subscribe(@RequestBody Subscription input,@RequestHeader(value="User-Agent",required=false) String userAgent){if(!push.enabled())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"WEB_PUSH_NOT_CONFIGURED","Web Push todavía no está configurado en el servidor");validate(input);UUID id=UUID.randomUUID();db.update("INSERT INTO web_push_subscription(id,user_id,endpoint,p256dh,auth,device_name,user_agent) VALUES(?,?,?,?,?,?,?) ON CONFLICT(endpoint) DO UPDATE SET user_id=EXCLUDED.user_id,p256dh=EXCLUDED.p256dh,auth=EXCLUDED.auth,device_name=EXCLUDED.device_name,user_agent=EXCLUDED.user_agent,enabled=TRUE,last_seen_at=CURRENT_TIMESTAMP",id,CurrentUser.id(),input.endpoint().trim(),input.p256dh().trim(),input.auth().trim(),clean(input.deviceName()),clean(userAgent));return Map.of("subscribed",true);}
  @DeleteMapping("/subscriptions") void unsubscribe(@RequestBody Endpoint input){if(input.endpoint()!=null)db.update("DELETE FROM web_push_subscription WHERE user_id=? AND endpoint=?",CurrentUser.id(),input.endpoint());}
  @PostMapping("/test") void test(){push.sendTest(CurrentUser.id());}
  @PostMapping("/rest-timers") void scheduleRest(@RequestBody RestTimer input){if(input==null||input.timerId()==null||input.endAt()==null||input.sessionId()==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REST_TIMER","El temporizador de descanso no es válido");Instant now=Instant.now();if(input.endAt().isBefore(now.minusSeconds(5))||input.endAt().isAfter(now.plusSeconds(3600)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REST_TIMER_TIME","La hora del descanso no es válida");Boolean active=db.queryForObject("SELECT EXISTS(SELECT 1 FROM workout_session WHERE id=? AND user_id=? AND status IN ('IN_PROGRESS','PAUSED'))",Boolean.class,input.sessionId(),CurrentUser.id());if(!Boolean.TRUE.equals(active))throw new ApiException(HttpStatus.CONFLICT,"REST_TIMER_SESSION_NOT_ACTIVE","El entrenamiento ya no está activo");db.update("INSERT INTO web_push_rest_timer(id,user_id,workout_session_id,client_timer_id,end_at,status) VALUES(?,?,?,?,?, 'PENDING') ON CONFLICT(user_id,client_timer_id) DO UPDATE SET workout_session_id=EXCLUDED.workout_session_id,end_at=EXCLUDED.end_at,status='PENDING',sent_at=NULL",UUID.randomUUID(),CurrentUser.id(),input.sessionId(),input.timerId(),input.endAt());}
  @DeleteMapping("/rest-timers/{timerId}") void cancelRest(@PathVariable UUID timerId){db.update("DELETE FROM web_push_rest_timer WHERE user_id=? AND client_timer_id=? AND status='PENDING'",CurrentUser.id(),timerId);}
  private static void validate(Subscription input){try{URI endpoint=URI.create(input.endpoint());if(!"https".equalsIgnoreCase(endpoint.getScheme()))throw new IllegalArgumentException();}catch(Exception exception){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PUSH_SUBSCRIPTION","La suscripción push no es válida");}if(input.p256dh()==null||input.p256dh().length()<40||input.auth()==null||input.auth().length()<8)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PUSH_KEYS","Las claves de la suscripción push no son válidas");}
  private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}record Subscription(String endpoint,String p256dh,String auth,String deviceName){}record Endpoint(String endpoint){}record RestTimer(UUID timerId,Instant endAt,UUID sessionId){}
}
