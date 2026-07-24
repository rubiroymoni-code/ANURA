package app.anura.workout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.anura.config.CurrentUser;
import app.anura.error.ApiException;

@Service
public class WorkoutPlanService {
 private final JdbcTemplate db; WorkoutPlanService(JdbcTemplate db){this.db=db;}
 public List<Plan> list(){return db.query("SELECT id,external_id,name,version,status,valid_from,valid_until,created_at FROM workout_plan WHERE user_id=? ORDER BY created_at DESC",(r,n)->new Plan(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getString(5),r.getObject(6,LocalDate.class),r.getObject(7,LocalDate.class),r.getTimestamp(8).toInstant()),CurrentUser.id());}
 public Plan one(UUID id){return list().stream().filter(p->p.id.equals(id)).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","Plan no encontrado"));}
 public Plan active(){return list().stream().filter(p->"ACTIVE".equals(p.status)).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ACTIVE_PLAN_NOT_FOUND","No hay plan activo"));}
 public List<Day> days(UUID id){one(id);return db.query("SELECT d.id,d.week_number,d.day_number,d.day_name,d.session_name,(SELECT count(*) FROM planned_exercise p WHERE p.workout_plan_day_id=d.id) FROM workout_plan_day d WHERE d.workout_plan_id=? ORDER BY d.week_number,d.day_order",(r,n)->new Day(r.getObject(1,UUID.class),r.getInt(2),r.getInt(3),r.getString(4),r.getString(5),r.getInt(6)),id);}
 @Transactional public Plan activate(UUID id){Plan plan=one(id);UUID user=CurrentUser.id();db.update("UPDATE workout_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='ACTIVE' AND id<>?",user,id);db.update("UPDATE workout_plan SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,user);return one(id);}
 public Plan archive(UUID id){one(id);db.update("UPDATE workout_plan SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,CurrentUser.id());return one(id);}
 public record Plan(UUID id,String externalId,String name,int version,String status,LocalDate validFrom,LocalDate validUntil,Instant createdAt){}
 public record Day(UUID id,int weekNumber,int dayNumber,String dayName,String sessionName,int exerciseCount){}
}
