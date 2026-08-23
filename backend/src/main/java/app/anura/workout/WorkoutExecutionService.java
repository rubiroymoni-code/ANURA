package app.anura.workout;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutExecutionService {
    private final JdbcTemplate db;
    private final int maxSyncOperations;
    private final int maxEstimateReps;

    WorkoutExecutionService(JdbcTemplate db,
        @Value("${app.workout.sync-max-operations:100}") int maxSyncOperations,
        @Value("${app.workout.estimated-1rm-max-reps:12}") int maxEstimateReps) {
        this.db = db; this.maxSyncOperations = maxSyncOperations; this.maxEstimateReps = maxEstimateReps;
    }

    public TodayWorkoutStatus todayStatus() {
        List<TodayWorkout> workouts=workouts(LocalDate.now());
        return new TodayWorkoutStatus(workouts.stream().findFirst().orElse(null), todayAdjustment(),workouts);
    }

    public List<WorkoutDayAdjustment> planAdjustments(UUID planId) {
        UUID user = CurrentUser.id();
        if (db.queryForObject("SELECT count(*) FROM workout_plan WHERE id=? AND user_id=? AND status='ACTIVE'", Integer.class, planId, user) == 0)
            throw notFound();
        return db.query("""
            SELECT a.workout_plan_day_id,a.original_date,a.scheduled_date,a.status,a.reason,d.session_name,d.day_number
            FROM workout_day_adjustment a
            JOIN workout_plan_day d ON d.id=a.workout_plan_day_id
            WHERE a.user_id=? AND a.workout_plan_id=?
            AND a.original_date>=CURRENT_DATE-7 AND a.original_date<=CURRENT_DATE+14
            ORDER BY a.original_date
            """, (r, n) -> new WorkoutDayAdjustment(
                r.getObject(1, UUID.class),
                r.getObject(2, LocalDate.class),
                r.getObject(3, LocalDate.class),
                r.getString(4),
                r.getString(5),
                r.getString(6),
                r.getInt(7)), user, planId);
    }

    private TodayAdjustment todayAdjustment() {
        UUID user = CurrentUser.id();
        List<TodayAdjustment> rows = db.query("""
            SELECT a.status,a.scheduled_date,d.session_name,a.reason
            FROM workout_day_adjustment a
            JOIN workout_plan_day d ON d.id=a.workout_plan_day_id
            JOIN workout_plan p ON p.id=a.workout_plan_id
            WHERE a.user_id=? AND p.status='ACTIVE' AND a.original_date=CURRENT_DATE
            AND a.status IN ('MOVED','SKIPPED')
            LIMIT 1
            """, (r, n) -> new TodayAdjustment(
                r.getString(1),
                r.getObject(2, LocalDate.class),
                r.getString(3),
                r.getString(4)), user);
        return rows.stream().findFirst().orElse(null);
    }

    public TodayWorkout today() {
        return workouts(LocalDate.now()).stream().findFirst().orElse(null);
    }

    public List<TodayWorkout> workouts(LocalDate date) {
        UUID user = CurrentUser.id();
        activateDuePlan(user);
        if(date!=null) db.update("UPDATE workout_session s SET status='COMPLETED',completed_at=COALESCE(s.completed_at,CURRENT_TIMESTAMP),last_activity_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1,adherence_percent=(SELECT CASE WHEN COUNT(*)=0 THEN 100 ELSE ROUND(100.0*COUNT(*) FILTER(WHERE completed_at IS NOT NULL)/COUNT(*))::integer END FROM exercise_performance WHERE workout_session_id=s.id) WHERE s.user_id=? AND s.planned_date=? AND s.status IN ('IN_PROGRESS','PAUSED') AND EXISTS(SELECT 1 FROM exercise_performance ep WHERE ep.workout_session_id=s.id AND ep.activity_name IS NOT NULL AND ep.completed_at IS NOT NULL)",user,date);
        if(date==null||date.isBefore(LocalDate.now().minusDays(7))||date.isAfter(LocalDate.now().plusDays(14))) throw bad("INVALID_WORKOUT_DATE","Consulta una fecha entre los últimos 7 y los próximos 14 días");
        return db.query("""
            SELECT p.id,p.name,p.version,d.id,d.session_name,d.day_name,d.week_number,d.day_number,
              COALESCE(SUM(COALESCE(pe.rest_seconds,0)+COALESCE(pe.sets,0)*90)/60,0),COUNT(pe.id)
            FROM workout_plan p JOIN workout_plan_day d ON d.workout_plan_id=p.id
            LEFT JOIN planned_exercise pe ON pe.workout_plan_day_id=d.id
            WHERE p.user_id=? AND p.status='ACTIVE'
            AND NOT EXISTS(SELECT 1 FROM workout_session s WHERE s.user_id=p.user_id AND s.workout_plan_day_id=d.id AND s.planned_date=? AND s.status IN ('COMPLETED','ABANDONED') AND s.deleted_at IS NULL)
            AND (
              EXISTS(SELECT 1 FROM workout_day_adjustment a WHERE a.user_id=p.user_id AND a.workout_plan_day_id=d.id AND a.status='MOVED' AND a.scheduled_date=?)
              OR (
                CASE
                  WHEN p.valid_from IS NOT NULL THEN
                    d.day_number=? AND d.week_number=(MOD(GREATEST((?-p.valid_from)/7,0),(SELECT MAX(cycle_day.week_number) FROM workout_plan_day cycle_day WHERE cycle_day.workout_plan_id=p.id))+1)
                  ELSE d.day_number=?
                END
                AND NOT EXISTS(SELECT 1 FROM workout_day_adjustment a WHERE a.user_id=p.user_id AND a.workout_plan_day_id=d.id AND a.original_date=?)
              )
            )
            GROUP BY p.id,p.name,p.version,d.id,d.session_name,d.day_name,d.week_number,d.day_number,d.day_order
            ORDER BY CASE WHEN EXISTS(SELECT 1 FROM workout_day_adjustment a WHERE a.user_id=p.user_id AND a.workout_plan_day_id=d.id AND a.status='MOVED' AND a.scheduled_date=?) THEN 0 ELSE 1 END,d.week_number,d.day_order
            """, (r,n)->new TodayWorkout(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getObject(4,UUID.class),r.getString(5),r.getString(6),r.getInt(7),r.getInt(8),r.getInt(9),r.getInt(10), planned(r.getObject(4,UUID.class))), user,date,date,date.getDayOfWeek().getValue(),date,date.getDayOfWeek().getValue(),date,date);
    }

    @Transactional public TodayWorkout rescheduleToday(LocalDate target,boolean force) {
        TodayWorkout workout=today(); if(workout==null) throw notFound();
        rescheduleDay(workout.dayId(),LocalDate.now(),target,force); return workout;
    }

    @Transactional public void skipToday(String reason) {
        TodayWorkout workout=today(); if(workout==null) throw notFound(); skipDay(workout.dayId(),LocalDate.now(),reason);
    }

    @Transactional public void rescheduleDay(UUID dayId,LocalDate original,LocalDate target,boolean force) {
        Map<String,Object> day=ownedPlanDay(dayId); LocalDate current=LocalDate.now();
        if(original==null||original.isBefore(current.minusDays(6))||original.isAfter(current.plusDays(7))||target==null||target.equals(original)||target.isBefore(current.minusDays(6))||target.isAfter(current.plusDays(14))) throw bad("INVALID_WORKOUT_DATE","Elige una fecha entre los últimos 7 y los próximos 14 días");
        UUID user=CurrentUser.id();
        List<LocalDate> previousSchedules=db.query("SELECT scheduled_date FROM workout_day_adjustment WHERE user_id=? AND workout_plan_day_id=? AND original_date=? AND status='MOVED'",(r,n)->r.getObject(1,LocalDate.class),user,dayId,original);
        LocalDate recordedDate=previousSchedules.isEmpty()?original:previousSchedules.getFirst();
        Integer targetWorkouts=db.queryForObject("""
            SELECT COUNT(DISTINCT d.id) FROM workout_plan_day d
            JOIN workout_plan p ON p.id=d.workout_plan_id
            WHERE p.user_id=? AND p.status='ACTIVE' AND d.id<>? AND (
              (d.day_number=EXTRACT(ISODOW FROM ?::date)::int AND (p.valid_from IS NULL OR d.week_number=(MOD(GREATEST((?-p.valid_from)/7,0),(SELECT MAX(cycle_day.week_number) FROM workout_plan_day cycle_day WHERE cycle_day.workout_plan_id=p.id))+1))
               AND NOT EXISTS(SELECT 1 FROM workout_day_adjustment away WHERE away.user_id=p.user_id AND away.workout_plan_day_id=d.id AND away.original_date=?))
              OR EXISTS(SELECT 1 FROM workout_day_adjustment moved WHERE moved.user_id=p.user_id AND moved.workout_plan_day_id=d.id AND moved.status='MOVED' AND moved.scheduled_date=?))
            """,Integer.class,user,dayId,target,target,target,target);
        if(!force&&targetWorkouts!=null&&targetWorkouts>0) throw conflict("WORKOUT_DATE_OCCUPIED","Ya tienes otro entrenamiento previsto ese día. Puedes confirmar el movimiento igualmente o elegir otra fecha");
        int chained=db.update("UPDATE workout_day_adjustment SET scheduled_date=?,status='MOVED',reason=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND workout_plan_day_id=? AND scheduled_date=? AND status='MOVED'",target,user,dayId,original);
        if(chained==0) db.update("INSERT INTO workout_day_adjustment(id,user_id,workout_plan_id,workout_plan_day_id,original_date,scheduled_date,status) VALUES(?,?,?,?,?,?,'MOVED') ON CONFLICT(user_id,workout_plan_day_id,original_date) DO UPDATE SET scheduled_date=EXCLUDED.scheduled_date,status='MOVED',reason=NULL,updated_at=CURRENT_TIMESTAMP",UUID.randomUUID(),user,day.get("plan_id"),dayId,original,target);
        else db.update("DELETE FROM workout_day_adjustment WHERE user_id=? AND workout_plan_day_id=? AND original_date=?",user,dayId,original);
        db.update("UPDATE workout_session SET planned_date=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=? AND workout_plan_day_id=? AND planned_date=? AND status='COMPLETED' AND deleted_at IS NULL",target,user,dayId,recordedDate);
        audit("WORKOUT_DAY_MOVED","WORKOUT_PLAN_DAY",dayId,"SUCCESS");
    }

    @Transactional public void skipDay(UUID dayId,LocalDate original,String reason) {
        Map<String,Object> day=ownedPlanDay(dayId); UUID user=CurrentUser.id(); LocalDate current=LocalDate.now();
        if(original==null||original.isBefore(current.minusDays(6))||original.isAfter(current.plusDays(7))) throw bad("INVALID_WORKOUT_DATE","La sesión debe pertenecer a los últimos 7 o próximos 7 días");
        if(db.queryForObject("SELECT count(*) FROM workout_session WHERE user_id=? AND workout_plan_day_id=? AND planned_date=? AND deleted_at IS NULL",Integer.class,user,dayId,original)>0) throw conflict("WORKOUT_ALREADY_RECORDED","Esta sesión ya tiene un registro");
        db.update("INSERT INTO workout_day_adjustment(id,user_id,workout_plan_id,workout_plan_day_id,original_date,status,reason) VALUES(?,?,?,?,?,'SKIPPED',?) ON CONFLICT(user_id,workout_plan_day_id,original_date) DO UPDATE SET scheduled_date=NULL,status='SKIPPED',reason=EXCLUDED.reason,updated_at=CURRENT_TIMESTAMP",UUID.randomUUID(),user,day.get("plan_id"),dayId,original,reason);
        db.update("INSERT INTO workout_session(id,user_id,workout_plan_id,workout_plan_version,workout_plan_day_id,session_name,planned_date,started_at,last_activity_at,status,client_external_id,adherence_reason,adherence_percent) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'ABANDONED',?,?,0)",UUID.randomUUID(),user,day.get("plan_id"),day.get("version"),dayId,day.get("session_name"),original,UUID.randomUUID(),reason==null?"No disponible":reason);
        audit("WORKOUT_DAY_SKIPPED","WORKOUT_PLAN_DAY",dayId,"SUCCESS");
    }

    private Map<String,Object> ownedPlanDay(UUID dayId){return db.queryForList("SELECT d.id,d.workout_plan_id plan_id,d.session_name,p.version FROM workout_plan_day d JOIN workout_plan p ON p.id=d.workout_plan_id WHERE d.id=? AND p.user_id=? AND p.status='ACTIVE'",dayId,CurrentUser.id()).stream().findFirst().orElseThrow(()->notFound());}

    public SessionView active() {
        return db.query("SELECT id FROM workout_session WHERE user_id=? AND status IN ('IN_PROGRESS','PAUSED') AND deleted_at IS NULL ORDER BY started_at DESC LIMIT 1", (r,n)->view(r.getObject(1,UUID.class)), CurrentUser.id()).stream().findFirst().orElse(null);
    }

    @Transactional
    public SessionView start(StartRequest request) {
        UUID user=CurrentUser.id();
        SessionView current=active();
        if(current!=null) {
            if(current.clientExternalId().equals(request.clientExternalId())) return current;
            throw conflict("ACTIVE_SESSION_EXISTS","Ya hay un entrenamiento activo");
        }
        UUID id=UUID.randomUUID(); LocalDate date=request.plannedDate()==null?LocalDate.now():request.plannedDate();
        if(request.workoutPlanDayId()!=null) {
            Map<String,Object> day=db.queryForMap("""
                SELECT d.id day_id,d.session_name,p.id plan_id,p.version FROM workout_plan_day d JOIN workout_plan p ON p.id=d.workout_plan_id
                WHERE d.id=? AND p.user_id=? AND p.status='ACTIVE'""",request.workoutPlanDayId(),user);
            db.update("INSERT INTO workout_session(id,user_id,workout_plan_id,workout_plan_version,workout_plan_day_id,session_name,planned_date,started_at,last_activity_at,status,client_external_id) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'IN_PROGRESS',?)",
                id,user,day.get("plan_id"),day.get("version"),day.get("day_id"),day.get("session_name"),date,request.clientExternalId());
            db.update("""
                INSERT INTO exercise_performance(id,workout_session_id,planned_exercise_id,exercise_id,exercise_order,target_sets,target_reps_min,target_reps_max,target_rir,target_rpe,target_rest_seconds,target_instructions)
                SELECT gen_random_uuid(),?,pe.id,pe.exercise_id,pe.exercise_order,pe.sets,pe.reps_min,pe.reps_max,pe.target_rir,pe.target_rpe,pe.rest_seconds,pe.instructions
                FROM planned_exercise pe WHERE pe.workout_plan_day_id=? ORDER BY pe.exercise_order""",id,request.workoutPlanDayId());
        } else {
            if(request.name()==null || request.name().isBlank()) throw bad("NAME_REQUIRED","Indica el nombre de la sesión libre");
            db.update("INSERT INTO workout_session(id,user_id,session_name,planned_date,started_at,last_activity_at,status,client_external_id) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'IN_PROGRESS',?)",id,user,request.name().trim(),date,request.clientExternalId());
        }
        audit("SESSION_STARTED","WORKOUT_SESSION",id,"SUCCESS"); return view(id);
    }

    public List<SessionSummary> history(int page,int size) {
        int safe=Math.min(Math.max(size,1),50), offset=Math.max(page,0)*safe;
        return db.query("""
            SELECT s.id,s.session_name,s.planned_date,s.status,s.workout_plan_day_id,s.started_at,s.completed_at,s.duration_seconds,s.global_rpe,
              COUNT(DISTINCT ep.id),COUNT(sp.id),COALESCE(SUM(CASE WHEN sp.weight IS NOT NULL AND sp.repetitions IS NOT NULL THEN sp.weight*sp.repetitions ELSE 0 END),0),
              COALESCE((SELECT SUM(activity_calories) FROM exercise_performance activity WHERE activity.workout_session_id=s.id),0)
            FROM workout_session s LEFT JOIN exercise_performance ep ON ep.workout_session_id=s.id LEFT JOIN set_performance sp ON sp.exercise_performance_id=ep.id AND sp.deleted_at IS NULL AND sp.completed
            WHERE s.user_id=? AND s.deleted_at IS NULL GROUP BY s.id ORDER BY s.planned_date DESC,s.started_at DESC LIMIT ? OFFSET ?""",
            (r,n)->new SessionSummary(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,LocalDate.class),r.getString(4),r.getObject(5,UUID.class),r.getTimestamp(6).toInstant(),instant(r,7),integer(r,8),decimal(r,9),r.getInt(10),r.getInt(11),r.getBigDecimal(12),r.getBigDecimal(13)),CurrentUser.id(),safe,offset);
    }

    public SessionView view(UUID id) {
        SessionHeader h=db.query("SELECT id,session_name,planned_date,status,started_at,completed_at,duration_seconds,global_rpe,energy_level,pump_level,pain_level,difficulty_level,notes,client_external_id,version,workout_plan_id,workout_plan_version,workout_plan_day_id,paused_at,paused_seconds FROM workout_session WHERE id=? AND user_id=? AND deleted_at IS NULL",this::header,id,CurrentUser.id()).stream().findFirst().orElseThrow(()->notFound());
        return new SessionView(h, exercises(id), metrics(id));
    }

    @Transactional public SessionView updateDuration(UUID id,Integer seconds) {
        view(id); if(seconds==null||seconds<1||seconds>86400) throw bad("INVALID_DURATION","Indica una duración válida");
        db.update("UPDATE workout_session SET duration_seconds=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=? AND status='COMPLETED'",seconds,id,CurrentUser.id());
        return view(id);
    }

    @Transactional public void deleteSession(UUID id) {
        SessionView session=view(id);
        if(List.of("IN_PROGRESS","PAUSED").contains(session.header().status())) throw conflict("ACTIVE_SESSION_DELETE","Cancela primero el entrenamiento en curso");
        db.update("DELETE FROM workout_personal_record WHERE source_set_performance_id IN (SELECT sp.id FROM set_performance sp JOIN exercise_performance ep ON ep.id=sp.exercise_performance_id WHERE ep.workout_session_id=?)",id);
        db.update("UPDATE workout_session SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted_at IS NULL",id,CurrentUser.id());
        audit("SESSION_DELETED","WORKOUT_SESSION",id,"SUCCESS");
    }

    @Transactional public SessionView transition(UUID id,String target,String event) {
        SessionView current=view(id); String status=current.header().status();
        boolean valid=(target.equals("PAUSED")&&status.equals("IN_PROGRESS"))||(target.equals("IN_PROGRESS")&&status.equals("PAUSED"))||(target.equals("ABANDONED")&&(status.equals("PAUSED")||status.equals("IN_PROGRESS")));
        if(!valid) throw conflict("INVALID_SESSION_STATE","La sesión no admite esta transición");
        if(target.equals("PAUSED")) db.update("UPDATE workout_session SET status='PAUSED',paused_at=CURRENT_TIMESTAMP,last_activity_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=?",id,CurrentUser.id());
        else if(target.equals("IN_PROGRESS")) db.update("UPDATE workout_session SET status='IN_PROGRESS',paused_seconds=paused_seconds+COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-paused_at))::integer,0),paused_at=NULL,last_activity_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=?",id,CurrentUser.id());
        else db.update("UPDATE workout_session SET status=?,last_activity_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=?",target,id,CurrentUser.id()); audit(event,"WORKOUT_SESSION",id,"SUCCESS"); return view(id);
    }

    @Transactional public SessionView complete(UUID id, CompleteRequest r) {
        SessionView current=view(id); if(!List.of("IN_PROGRESS","PAUSED").contains(current.header().status())) throw conflict("INVALID_SESSION_STATE","La sesión ya está cerrada");
        validateLevel(r.globalRpe(),1,"RPE"); validateLevel(r.energyLevel(),0,"energía"); validateLevel(r.pumpLevel(),0,"congestión"); validateLevel(r.painLevel(),0,"dolor"); validateLevel(r.difficultyLevel(),0,"dificultad");
        db.update("UPDATE workout_session SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,last_activity_at=CURRENT_TIMESTAMP,duration_seconds=GREATEST(COALESCE(duration_seconds,0),COALESCE(?,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-started_at))::integer-paused_seconds-CASE WHEN paused_at IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-paused_at))::integer END)),paused_at=NULL,global_rpe=?,energy_level=?,pump_level=?,pain_level=?,difficulty_level=?,notes=?,adherence_reason=?,adherence_percent=(SELECT CASE WHEN COUNT(*)=0 THEN 100 ELSE ROUND(100.0*COUNT(*) FILTER(WHERE completed_at IS NOT NULL)/COUNT(*))::integer END FROM exercise_performance WHERE workout_session_id=?),updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=?",r.durationSeconds(),r.globalRpe(),r.energyLevel(),r.pumpLevel(),r.painLevel(),r.difficultyLevel(),r.notes(),r.adherenceReason(),id,id,CurrentUser.id());
        detectRecords(id); audit("SESSION_COMPLETED","WORKOUT_SESSION",id,"SUCCESS"); return view(id);
    }

    @Transactional public SessionView abandon(UUID id,String reason){SessionView result=transition(id,"ABANDONED","SESSION_ABANDONED");db.update("UPDATE workout_session SET adherence_reason=?,adherence_percent=(SELECT CASE WHEN COUNT(*)=0 THEN 0 ELSE ROUND(100.0*COUNT(*) FILTER(WHERE completed_at IS NOT NULL)/COUNT(*))::integer END FROM exercise_performance WHERE workout_session_id=?) WHERE id=? AND user_id=?",reason,id,id,CurrentUser.id());return view(id);}

    @Transactional public ExerciseView addExercise(UUID sessionId, AddExerciseRequest r) {
        assertEditable(sessionId); UUID id=UUID.randomUUID(); int order=r.order()==null?db.queryForObject("SELECT COALESCE(MAX(exercise_order),0)+1 FROM exercise_performance WHERE workout_session_id=?",Integer.class,sessionId):r.order();
        int changed=db.update("INSERT INTO exercise_performance(id,workout_session_id,exercise_id,exercise_order,notes) SELECT ?,?,e.id,?,? FROM exercise e WHERE e.id=? AND e.active AND (e.owner_id IS NULL OR e.owner_id=?)",id,sessionId,order,r.notes(),r.exerciseId(),CurrentUser.id());
        if(changed==0) throw bad("EXERCISE_NOT_FOUND","Ejercicio no encontrado"); touch(sessionId); return exercise(id);
    }

    @Transactional public ExerciseView substitute(UUID sessionId,UUID performanceId,SubstituteRequest r) {
        assertExercise(sessionId,performanceId); int changed=db.update("UPDATE exercise_performance ep SET original_exercise_id=ep.exercise_id,exercise_id=?,substitution_reason=?,substitution_notes=?,updated_at=CURRENT_TIMESTAMP WHERE ep.id=? AND EXISTS(SELECT 1 FROM exercise e WHERE e.id=? AND e.active AND (e.owner_id IS NULL OR e.owner_id=?))",r.replacementExerciseId(),r.reason(),r.notes(),performanceId,r.replacementExerciseId(),CurrentUser.id());
        if(changed==0) throw bad("EXERCISE_NOT_FOUND","Sustituto no encontrado"); audit("EXERCISE_SUBSTITUTED","EXERCISE_PERFORMANCE",performanceId,"SUCCESS"); return exercise(performanceId);
    }

    @Transactional public ExerciseView pain(UUID sessionId,UUID performanceId,PainRequest r) {
        assertExercise(sessionId,performanceId); validateLevel(r.intensity(),0,"dolor"); db.update("UPDATE exercise_performance SET pain_reported=?,pain_area=?,pain_intensity=?,notes=COALESCE(?,notes),updated_at=CURRENT_TIMESTAMP WHERE id=?",r.intensity()!=null&&r.intensity()>0,r.area(),r.intensity(),r.comment(),performanceId); audit("PAIN_REPORTED","EXERCISE_PERFORMANCE",performanceId,"SUCCESS"); return exercise(performanceId);
    }

    @Transactional public ExerciseView completeExercise(UUID sessionId,UUID performanceId) { assertExercise(sessionId,performanceId); db.update("UPDATE exercise_performance SET completed_at=CASE WHEN completed_at IS NULL THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE id=?",performanceId); touch(sessionId); return exercise(performanceId); }
    @Transactional public ExerciseView recordActivity(UUID sessionId,UUID performanceId,ActivityRequest request) {assertExercise(sessionId,performanceId);if(request==null||request.name()==null||request.name().isBlank()||request.minutes()==null||request.minutes()<1||request.calories()==null||request.calories().signum()<0)throw bad("INVALID_ACTIVITY","Indica una actividad, duración y calorías válidas");db.update("UPDATE exercise_performance SET activity_name=?,activity_minutes=?,activity_calories=?,notes=COALESCE(?,notes),completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",request.name().trim(),request.minutes(),request.calories(),request.notes(),performanceId);db.update("UPDATE workout_session SET duration_seconds=GREATEST(COALESCE(duration_seconds,0),?),updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=?",request.minutes()*60,sessionId,CurrentUser.id());return exercise(performanceId);}

    @Transactional public SetView addSet(UUID sessionId,UUID performanceId,SetRequest r) {
        assertExercise(sessionId,performanceId); validateSet(r); UUID client=r.clientExternalId()==null?UUID.randomUUID():r.clientExternalId();
        List<SetView> existing=db.query("SELECT id FROM set_performance WHERE exercise_performance_id=? AND client_external_id=?",(rs,n)->set(rs.getObject(1,UUID.class)),performanceId,client); if(!existing.isEmpty()) return existing.getFirst();
        db.update("DELETE FROM workout_personal_record WHERE source_set_performance_id IN (SELECT id FROM set_performance WHERE exercise_performance_id=? AND deleted_at IS NOT NULL)",performanceId);
        db.update("DELETE FROM set_performance WHERE exercise_performance_id=? AND deleted_at IS NOT NULL",performanceId);
        UUID id=UUID.randomUUID(); int number=db.queryForObject("SELECT COALESCE(MAX(set_number),0)+1 FROM set_performance WHERE exercise_performance_id=?",Integer.class,performanceId);
        db.update("INSERT INTO set_performance(id,exercise_performance_id,set_number,set_type,weight,repetitions,rir,rpe,duration_seconds,distance_meters,rest_seconds,tempo,pain_level,completed,performed_at,client_external_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,?)",id,performanceId,number,r.setType()==null?"WORKING":r.setType(),r.weight(),r.repetitions(),r.rir(),r.rpe(),r.durationSeconds(),r.distanceMeters(),r.restSeconds(),r.tempo(),r.painLevel(),r.completed(),r.completed(),client);
        touch(sessionId); return set(id);
    }

    @Transactional public SetView updateSet(UUID sessionId,UUID performanceId,UUID setId,SetRequest r) {
        assertExercise(sessionId,performanceId); validateSet(r); int changed=db.update("UPDATE set_performance SET set_type=?,weight=?,repetitions=?,rir=?,rpe=?,duration_seconds=?,distance_meters=?,rest_seconds=?,tempo=?,pain_level=?,completed=?,performed_at=CASE WHEN ? THEN COALESCE(performed_at,CURRENT_TIMESTAMP) ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE id=? AND exercise_performance_id=? AND deleted_at IS NULL",r.setType()==null?"WORKING":r.setType(),r.weight(),r.repetitions(),r.rir(),r.rpe(),r.durationSeconds(),r.distanceMeters(),r.restSeconds(),r.tempo(),r.painLevel(),r.completed(),r.completed(),setId,performanceId); if(changed==0) throw notFound(); touch(sessionId); return set(setId);
    }

    @Transactional public void deleteSet(UUID sessionId,UUID performanceId,UUID setId) { assertExercise(sessionId,performanceId); db.update("DELETE FROM workout_personal_record WHERE source_set_performance_id=?",setId); if(db.update("DELETE FROM set_performance WHERE id=? AND exercise_performance_id=? AND deleted_at IS NULL",setId,performanceId)==0) throw notFound(); touch(sessionId); }

    public List<ExerciseHistory> exerciseHistory(UUID exerciseId,int limit) {
        return db.query("""
            SELECT s.id,s.planned_date,s.session_name,sp.weight,sp.repetitions,sp.rir,sp.rpe,sp.set_number
            FROM workout_session s JOIN exercise_performance ep ON ep.workout_session_id=s.id
            JOIN set_performance sp ON sp.exercise_performance_id=ep.id
            WHERE s.user_id=? AND ep.exercise_id=? AND s.status='COMPLETED' AND s.deleted_at IS NULL AND sp.completed
              AND sp.deleted_at IS NULL
            ORDER BY s.completed_at DESC,sp.set_number LIMIT ?
            """,(r,n)->new ExerciseHistory(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),r.getString(3),decimal(r,4),integer(r,5),decimal(r,6),decimal(r,7),r.getInt(8)),CurrentUser.id(),exerciseId,Math.min(Math.max(limit,1),100));
    }

    public List<ExerciseOption> exerciseCatalog() {
        return db.query("SELECT id,name,muscle_group,equipment FROM exercise WHERE active AND (owner_id IS NULL OR owner_id=?) ORDER BY muscle_group,name",(r,n)->new ExerciseOption(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),CurrentUser.id());
    }

    @Transactional public ExerciseOption createCustomExercise(CustomExerciseRequest request){
        String name=request==null||request.name()==null?"":request.name().trim(),group=request==null||request.muscleGroup()==null?"Otro":request.muscleGroup().trim();
        if(name.length()<2||name.length()>180)throw bad("EXERCISE_NAME_INVALID","Escribe un nombre de ejercicio válido");
        List<ExerciseOption> existing=db.query("SELECT id,name,muscle_group,equipment FROM exercise WHERE active AND lower(trim(name))=lower(?) AND (owner_id IS NULL OR owner_id=?) ORDER BY owner_id NULLS FIRST LIMIT 1",(r,n)->new ExerciseOption(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),name,CurrentUser.id());
        if(!existing.isEmpty())return existing.getFirst();UUID id=UUID.randomUUID();
        db.update("INSERT INTO exercise(id,code,name,muscle_group,equipment,owner_id) VALUES(?,?,?,?,?,?)",id,"CUSTOM_"+CurrentUser.id()+"_"+UUID.randomUUID(),name,group,"Otro",CurrentUser.id());
        return new ExerciseOption(id,name,group,"Otro");
    }

    public Metrics metrics(UUID sessionId) {
        if(db.queryForObject("SELECT count(*) FROM workout_session WHERE id=? AND user_id=?",Integer.class,sessionId,CurrentUser.id())==0) throw notFound();
        return db.queryForObject("""
            SELECT COUNT(DISTINCT ep.id),COUNT(sp.id),COALESCE(SUM(sp.repetitions),0),
              COALESCE(SUM(CASE WHEN sp.weight IS NOT NULL AND sp.repetitions IS NOT NULL THEN sp.weight*sp.repetitions ELSE 0 END),0),
              COALESCE(MAX(sp.pain_level),0),COUNT(pr.id)
            FROM exercise_performance ep
            LEFT JOIN set_performance sp ON sp.exercise_performance_id=ep.id AND sp.completed AND sp.deleted_at IS NULL
            LEFT JOIN workout_personal_record pr ON pr.source_set_performance_id=sp.id
            WHERE ep.workout_session_id=?
            """,(r,n)->new Metrics(r.getInt(1),r.getInt(2),r.getInt(3),r.getBigDecimal(4),r.getInt(5),r.getInt(6)),sessionId);
    }

    @Transactional public List<SyncResult> sync(UUID sessionId,List<SyncOperation> operations) {
        view(sessionId); if(operations.size()>maxSyncOperations) throw bad("SYNC_BATCH_TOO_LARGE","Demasiadas operaciones en el lote");
        return operations.stream().map(op->syncOne(sessionId,op)).toList();
    }

    private SyncResult syncOne(UUID sessionId,SyncOperation op) {
        List<SyncResult> done=db.query("SELECT result,result_entity_id,error_code FROM workout_sync_operation WHERE operation_id=? AND user_id=?",(r,n)->new SyncResult(op.operationId(),r.getString(1),r.getObject(2,UUID.class),r.getString(3)),op.operationId(),CurrentUser.id()); if(!done.isEmpty()) return done.getFirst();
        String result="APPLIED", error=null; UUID entity=null;
        try {
            if("ADD_SET".equals(op.operationType())) { JsonNode p=op.payload(); entity=addSet(sessionId,UUID.fromString(p.path("exercisePerformanceId").asText()),new SetRequest(null,p.path("setType").asText("WORKING"),decimal(p,"weight"),integer(p,"repetitions"),decimal(p,"rir"),decimal(p,"rpe"),integer(p,"durationSeconds"),decimal(p,"distanceMeters"),integer(p,"restSeconds"),text(p,"tempo"),integer(p,"painLevel"),p.path("completed").asBoolean(false),op.clientEntityId())).id(); }
            else if("UPDATE_SET".equals(op.operationType())) { JsonNode p=op.payload(); entity=UUID.fromString(p.path("setId").asText()); updateSet(sessionId,UUID.fromString(p.path("exercisePerformanceId").asText()),entity,new SetRequest(null,p.path("setType").asText("WORKING"),decimal(p,"weight"),integer(p,"repetitions"),decimal(p,"rir"),decimal(p,"rpe"),integer(p,"durationSeconds"),decimal(p,"distanceMeters"),integer(p,"restSeconds"),text(p,"tempo"),integer(p,"painLevel"),p.path("completed").asBoolean(false),op.clientEntityId())); }
            else if("DELETE_SET".equals(op.operationType())) { JsonNode p=op.payload(); entity=UUID.fromString(p.path("setId").asText()); deleteSet(sessionId,UUID.fromString(p.path("exercisePerformanceId").asText()),entity); }
            else if("COMPLETE_SESSION".equals(op.operationType())) { JsonNode p=op.payload(); complete(sessionId,new CompleteRequest(decimal(p,"globalRpe"),integer(p,"energyLevel"),integer(p,"pumpLevel"),integer(p,"painLevel"),integer(p,"difficultyLevel"),text(p,"notes"),text(p,"adherenceReason"))); entity=sessionId; }
            else if("PAUSE_SESSION".equals(op.operationType())) transition(sessionId,"PAUSED","SESSION_PAUSED");
            else if("RESUME_SESSION".equals(op.operationType())) transition(sessionId,"IN_PROGRESS","SESSION_RESUMED");
            else throw bad("UNSUPPORTED_SYNC_OPERATION","Operación offline no soportada");
        } catch(ApiException ex) { result=ex.status==HttpStatus.CONFLICT?"CONFLICT":"ERROR"; error=ex.code; }
        db.update("INSERT INTO workout_sync_operation(operation_id,user_id,workout_session_id,operation_type,entity_type,client_entity_id,occurred_at,result,result_entity_id,error_code) VALUES(?,?,?,?,?,?,?,?,?,?)",op.operationId(),CurrentUser.id(),sessionId,op.operationType(),op.entityType(),op.clientEntityId(),op.occurredAt(),result,entity,error);
        if("CONFLICT".equals(result)) audit("OFFLINE_SYNC_CONFLICT","WORKOUT_SESSION",sessionId,"CONFLICT"); else if("APPLIED".equals(result)) audit("OFFLINE_SYNC_COMPLETED","WORKOUT_SESSION",sessionId,"SUCCESS"); return new SyncResult(op.operationId(),result,entity,error);
    }

    private List<PlannedExerciseView> planned(UUID day) { return db.query("SELECT pe.id,e.id,e.name,e.muscle_group,e.equipment,pe.exercise_order,pe.sets,pe.reps_min,pe.reps_max,pe.target_rir,pe.target_rpe,pe.rest_seconds,pe.instructions FROM planned_exercise pe JOIN exercise e ON e.id=pe.exercise_id WHERE pe.workout_plan_day_id=? ORDER BY pe.exercise_order",(r,n)->new PlannedExerciseView(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getInt(6),r.getInt(7),r.getInt(8),r.getInt(9),decimal(r,10),decimal(r,11),integer(r,12),r.getString(13)),day); }
    private List<ExerciseView> exercises(UUID session) { return db.query("SELECT id FROM exercise_performance WHERE workout_session_id=? ORDER BY exercise_order",(r,n)->exercise(r.getObject(1,UUID.class)),session); }
    private ExerciseView exercise(UUID id) { return db.query("SELECT ep.id,ep.exercise_id,e.name,e.muscle_group,e.equipment,ep.exercise_order,ep.original_exercise_id,ep.substitution_reason,ep.pain_reported,ep.pain_area,ep.pain_intensity,ep.notes,ep.target_sets,ep.target_reps_min,ep.target_reps_max,ep.target_rir,ep.target_rpe,ep.target_rest_seconds,ep.target_instructions,ep.completed_at,ep.activity_name,ep.activity_minutes,ep.activity_calories FROM exercise_performance ep JOIN exercise e ON e.id=ep.exercise_id WHERE ep.id=?",(r,n)->new ExerciseView(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getInt(6),r.getObject(7,UUID.class),r.getString(8),r.getBoolean(9),r.getString(10),integer(r,11),r.getString(12),integer(r,13),integer(r,14),integer(r,15),decimal(r,16),decimal(r,17),integer(r,18),r.getString(19),r.getTimestamp(20)!=null,r.getString(21),integer(r,22),decimal(r,23),sets(id)),id).getFirst(); }
    private List<SetView> sets(UUID exercise) { return db.query("SELECT id FROM set_performance WHERE exercise_performance_id=? AND deleted_at IS NULL ORDER BY set_number",(r,n)->set(r.getObject(1,UUID.class)),exercise); }
    private SetView set(UUID id) { return db.query("SELECT id,set_number,set_type,weight,repetitions,rir,rpe,duration_seconds,distance_meters,rest_seconds,tempo,pain_level,completed,performed_at,client_external_id FROM set_performance WHERE id=? AND deleted_at IS NULL",(r,n)->new SetView(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),decimal(r,4),integer(r,5),decimal(r,6),decimal(r,7),integer(r,8),decimal(r,9),integer(r,10),r.getString(11),integer(r,12),r.getBoolean(13),instant(r,14),r.getObject(15,UUID.class)),id).getFirst(); }
    private SessionHeader header(ResultSet r,int n)throws SQLException{return new SessionHeader(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,LocalDate.class),r.getString(4),r.getTimestamp(5).toInstant(),instant(r,6),integer(r,7),decimal(r,8),integer(r,9),integer(r,10),integer(r,11),integer(r,12),r.getString(13),r.getObject(14,UUID.class),r.getLong(15),r.getObject(16,UUID.class),integer(r,17),r.getObject(18,UUID.class),instant(r,19),r.getInt(20));}
    private void assertEditable(UUID session){
        SessionHeader header=view(session).header();
        if(List.of("IN_PROGRESS","PAUSED").contains(header.status())) return;
        if(List.of("COMPLETED","ABANDONED").contains(header.status())) return;
        throw conflict("SESSION_NOT_EDITABLE","La sesión está cerrada");
    }
    private void assertExercise(UUID session,UUID exercise){assertEditable(session);if(db.queryForObject("SELECT count(*) FROM exercise_performance WHERE id=? AND workout_session_id=?",Integer.class,exercise,session)==0)throw notFound();}
    private void validateSet(SetRequest r){if(r.weight()!=null&&r.weight().signum()<0)throw bad("INVALID_WEIGHT","El peso no puede ser negativo");if(r.repetitions()!=null&&r.repetitions()<0)throw bad("INVALID_REPETITIONS","Las repeticiones no pueden ser negativas");validateDecimal(r.rir(),0,"RIR");validateDecimal(r.rpe(),1,"RPE");validateLevel(r.painLevel(),0,"dolor");}
    private void validateDecimal(BigDecimal n,int min,String field){if(n!=null&&(n.compareTo(BigDecimal.valueOf(min))<0||n.compareTo(BigDecimal.TEN)>0))throw bad("INVALID_"+field,"Valor de "+field+" fuera de rango");}
    private void validateLevel(Number n,int min,String field){if(n!=null&&(n.doubleValue()<min||n.doubleValue()>10))throw bad("INVALID_LEVEL","Valor de "+field+" fuera de rango");}
    private void touch(UUID session){db.update("UPDATE workout_session SET status=CASE WHEN status='ABANDONED' THEN 'COMPLETED' ELSE status END,completed_at=CASE WHEN status='ABANDONED' THEN CURRENT_TIMESTAMP ELSE completed_at END,last_activity_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1,adherence_percent=CASE WHEN status='ABANDONED' THEN (SELECT CASE WHEN COUNT(*)=0 THEN 100 ELSE ROUND(100.0*COUNT(*) FILTER(WHERE completed_at IS NOT NULL)/COUNT(*))::integer END FROM exercise_performance WHERE workout_session_id=?) ELSE adherence_percent END WHERE id=?",session,session);}
    private void detectRecords(UUID session){
        insertRecord(session,"MAX_WEIGHT","sp.weight","sp.weight IS NOT NULL");
        insertRecord(session,"MAX_VOLUME","sp.weight*sp.repetitions","sp.weight IS NOT NULL AND sp.repetitions IS NOT NULL");
        insertRecord(session,"MAX_REPETITIONS","sp.repetitions","sp.repetitions IS NOT NULL");
        db.update("""
            INSERT INTO workout_personal_record(id,user_id,exercise_id,record_type,value,source_set_performance_id,achieved_at)
            SELECT gen_random_uuid(),s.user_id,ep.exercise_id,'ESTIMATED_1RM',sp.weight*(1+sp.repetitions/30.0),sp.id,COALESCE(sp.performed_at,CURRENT_TIMESTAMP)
            FROM set_performance sp JOIN exercise_performance ep ON ep.id=sp.exercise_performance_id JOIN workout_session s ON s.id=ep.workout_session_id
            WHERE s.id=? AND sp.completed AND sp.weight IS NOT NULL AND sp.repetitions BETWEEN 1 AND ?
            AND NOT EXISTS(SELECT 1 FROM workout_personal_record pr WHERE pr.user_id=s.user_id AND pr.exercise_id=ep.exercise_id AND pr.record_type='ESTIMATED_1RM' AND pr.value>=sp.weight*(1+sp.repetitions/30.0)) ON CONFLICT DO NOTHING""",session,maxEstimateReps);
    }
    private void insertRecord(UUID session,String type,String expression,String condition){
        db.update("INSERT INTO workout_personal_record(id,user_id,exercise_id,record_type,value,source_set_performance_id,achieved_at) SELECT gen_random_uuid(),s.user_id,ep.exercise_id,?,"+expression+",sp.id,COALESCE(sp.performed_at,CURRENT_TIMESTAMP) FROM set_performance sp JOIN exercise_performance ep ON ep.id=sp.exercise_performance_id JOIN workout_session s ON s.id=ep.workout_session_id WHERE s.id=? AND sp.completed AND "+condition+" AND NOT EXISTS(SELECT 1 FROM workout_personal_record pr WHERE pr.user_id=s.user_id AND pr.exercise_id=ep.exercise_id AND pr.record_type=? AND pr.value>="+expression+") ON CONFLICT DO NOTHING",type,session,type);
    }
    private void activateDuePlan(UUID user){UUID due=db.query("SELECT id FROM workout_plan WHERE user_id=? AND status='DRAFT' AND valid_from<=CURRENT_DATE ORDER BY valid_from DESC,created_at DESC LIMIT 1",(r,n)->r.getObject(1,UUID.class),user).stream().findFirst().orElse(null);if(due==null)return;db.update("UPDATE workout_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='ACTIVE' AND id<>?",user,due);db.update("UPDATE workout_plan SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",due,user);}
    private void audit(String action,String type,UUID id,String result){db.update("INSERT INTO audit_log(id,actor_id,action,entity_type,entity_id,result,metadata) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),CurrentUser.id(),action,type,id,result,"workout-execution-v1");}
    public BigDecimal estimatedOneRepMax(BigDecimal weight,Integer reps){if(weight==null||reps==null||reps<=0||reps>maxEstimateReps)return null;return weight.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30),6,RoundingMode.HALF_UP))).setScale(2,RoundingMode.HALF_UP);}
    private static ApiException bad(String c,String m){return new ApiException(HttpStatus.BAD_REQUEST,c,m);} private static ApiException conflict(String c,String m){return new ApiException(HttpStatus.CONFLICT,c,m);} private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"WORKOUT_NOT_FOUND","Entrenamiento no encontrado");}
    private static Instant instant(ResultSet r,int i)throws SQLException{return r.getTimestamp(i)==null?null:r.getTimestamp(i).toInstant();} private static Integer integer(ResultSet r,int i)throws SQLException{Object x=r.getObject(i);return x==null?null:((Number)x).intValue();} private static BigDecimal decimal(ResultSet r,int i)throws SQLException{return r.getBigDecimal(i);} private static BigDecimal decimal(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).decimalValue():null;} private static Integer integer(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).intValue():null;} private static String text(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).asText():null;}

    public record TodayWorkout(UUID planId,String planName,int planVersion,UUID dayId,String sessionName,String dayName,int weekNumber,int dayNumber,int estimatedMinutes,int exerciseCount,List<PlannedExerciseView> exercises){}
    public record TodayAdjustment(String status,LocalDate scheduledDate,String sessionName,String reason){}
    public record TodayWorkoutStatus(TodayWorkout workout,TodayAdjustment adjustment,List<TodayWorkout> workouts){}
    public record WorkoutDayAdjustment(UUID dayId,LocalDate originalDate,LocalDate scheduledDate,String status,String reason,String sessionName,int dayNumber){}
    public record PlannedExerciseView(UUID plannedExerciseId,UUID exerciseId,String name,String muscleGroup,String equipment,int order,int sets,int repsMin,int repsMax,BigDecimal targetRir,BigDecimal targetRpe,Integer restSeconds,String instructions){}
    public record StartRequest(UUID workoutPlanDayId,String name,LocalDate plannedDate,UUID clientExternalId){public StartRequest{if(clientExternalId==null)clientExternalId=UUID.randomUUID();}}
    public record CompleteRequest(BigDecimal globalRpe,Integer energyLevel,Integer pumpLevel,Integer painLevel,Integer difficultyLevel,String notes,String adherenceReason,Integer durationSeconds){public CompleteRequest(BigDecimal globalRpe,Integer energyLevel,Integer pumpLevel,Integer painLevel,Integer difficultyLevel,String notes,String adherenceReason){this(globalRpe,energyLevel,pumpLevel,painLevel,difficultyLevel,notes,adherenceReason,null);}}
    public record AbandonRequest(String reason){}
    public record AddExerciseRequest(UUID exerciseId,Integer order,String notes){} public record SubstituteRequest(UUID replacementExerciseId,String reason,String notes){} public record PainRequest(Integer intensity,String area,String comment){} public record ActivityRequest(String name,Integer minutes,BigDecimal calories,String notes){}
    public record SetRequest(Integer setNumber,String setType,BigDecimal weight,Integer repetitions,BigDecimal rir,BigDecimal rpe,Integer durationSeconds,BigDecimal distanceMeters,Integer restSeconds,String tempo,Integer painLevel,boolean completed,UUID clientExternalId){}
    public record SessionHeader(UUID id,String name,LocalDate plannedDate,String status,Instant startedAt,Instant completedAt,Integer durationSeconds,BigDecimal globalRpe,Integer energyLevel,Integer pumpLevel,Integer painLevel,Integer difficultyLevel,String notes,UUID clientExternalId,long version,UUID workoutPlanId,Integer workoutPlanVersion,UUID workoutPlanDayId,Instant pausedAt,int pausedSeconds){}
    public record SessionView(SessionHeader header,List<ExerciseView> exercises,Metrics metrics){public UUID clientExternalId(){return header.clientExternalId();}}
    public record ExerciseView(UUID id,UUID exerciseId,String name,String muscleGroup,String equipment,int order,UUID originalExerciseId,String substitutionReason,boolean painReported,String painArea,Integer painIntensity,String notes,Integer targetSets,Integer targetRepsMin,Integer targetRepsMax,BigDecimal targetRir,BigDecimal targetRpe,Integer targetRestSeconds,String instructions,boolean completed,String activityName,Integer activityMinutes,BigDecimal activityCalories,List<SetView> sets){}
    public record SetView(UUID id,int setNumber,String setType,BigDecimal weight,Integer repetitions,BigDecimal rir,BigDecimal rpe,Integer durationSeconds,BigDecimal distanceMeters,Integer restSeconds,String tempo,Integer painLevel,boolean completed,Instant performedAt,UUID clientExternalId){}
    public record Metrics(int exercises,int sets,int repetitions,BigDecimal volume,int maxPain,int personalRecords){} public record SessionSummary(UUID id,String name,LocalDate date,String status,UUID workoutPlanDayId,Instant startedAt,Instant completedAt,Integer durationSeconds,BigDecimal globalRpe,int exercises,int sets,BigDecimal volume,BigDecimal activityCalories){}
    public record ExerciseHistory(UUID sessionId,LocalDate date,String sessionName,BigDecimal weight,Integer repetitions,BigDecimal rir,BigDecimal rpe,int setNumber){}
    public record ExerciseOption(UUID id,String name,String muscleGroup,String equipment){}
    public record CustomExerciseRequest(String name,String muscleGroup){}
    public record SyncOperation(UUID operationId,UUID clientEntityId,String operationType,String entityType,Instant occurredAt,Long baseVersion,JsonNode payload){public SyncOperation{if(operationId==null)operationId=UUID.randomUUID();if(occurredAt==null)occurredAt=Instant.now();}}
    public record SyncResult(UUID operationId,String result,UUID entityId,String errorCode){}
}
