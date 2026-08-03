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
 public List<PlanExercise> details(UUID id){one(id);return db.query("SELECT d.week_number,d.day_number,d.day_name,d.session_name,pe.exercise_order,e.name,e.muscle_group,e.equipment,pe.sets,pe.reps_min,pe.reps_max,pe.target_rir,pe.target_rpe,pe.rest_seconds,pe.tempo,pe.warmup_required,pe.superset_group,pe.alternative_exercise_code,pe.instructions,pe.notes FROM workout_plan_day d JOIN planned_exercise pe ON pe.workout_plan_day_id=d.id JOIN exercise e ON e.id=pe.exercise_id WHERE d.workout_plan_id=? ORDER BY d.week_number,d.day_order,pe.exercise_order",(r,n)->new PlanExercise(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5),r.getString(6),r.getString(7),r.getString(8),r.getInt(9),r.getInt(10),r.getInt(11),r.getBigDecimal(12),r.getBigDecimal(13),(Integer)r.getObject(14),r.getString(15),r.getBoolean(16),r.getString(17),r.getString(18),r.getString(19),r.getString(20)),id);}
 @Transactional public Plan activate(UUID id){Plan plan=one(id);UUID user=CurrentUser.id();db.update("UPDATE workout_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='ACTIVE' AND id<>?",user,id);db.update("UPDATE workout_plan SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,user);return one(id);}
 public Plan archive(UUID id){one(id);db.update("UPDATE workout_plan SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,CurrentUser.id());return one(id);}
 @Transactional public void delete(UUID id){Plan plan=one(id);UUID user=CurrentUser.id();db.update("UPDATE exercise_performance SET planned_exercise_id=NULL WHERE planned_exercise_id IN (SELECT pe.id FROM planned_exercise pe JOIN workout_plan_day d ON d.id=pe.workout_plan_day_id WHERE d.workout_plan_id=?)",id);db.update("UPDATE workout_session SET workout_plan_id=NULL,workout_plan_day_id=NULL WHERE workout_plan_id=? AND user_id=?",id,user);db.update("UPDATE import_job SET plan_id=NULL WHERE plan_id=?",id);db.update("DELETE FROM workout_plan WHERE id=? AND user_id=?",id,user);db.update("DELETE FROM import_job WHERE user_id=? AND import_type='TRAINING_PLAN' AND external_id=? AND plan_version=?",user,plan.externalId(),plan.version());}
 public record Plan(UUID id,String externalId,String name,int version,String status,LocalDate validFrom,LocalDate validUntil,Instant createdAt){}
 public record Day(UUID id,int weekNumber,int dayNumber,String dayName,String sessionName,int exerciseCount){}
 public record PlanExercise(int weekNumber,int dayNumber,String dayName,String sessionName,int order,String exercise,String muscleGroup,String equipment,int sets,int repsMin,int repsMax,java.math.BigDecimal targetRir,java.math.BigDecimal targetRpe,Integer restSeconds,String tempo,boolean warmupRequired,String supersetGroup,String alternativeExerciseCode,String instructions,String notes){}
}
