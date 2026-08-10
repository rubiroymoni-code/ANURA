package app.anura.household;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import app.anura.notification.EmailService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {
  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final EmailService emails;

  HouseholdController(JdbcTemplate db, PasswordEncoder encoder, EmailService emails) {
    this.db = db;
    this.encoder = encoder;
    this.emails = emails;
  }

  @GetMapping
  List<Map<String, Object>> mine() {
    return db.queryForList("SELECT h.id,h.name,m.role FROM household h JOIN household_member m ON m.household_id=h.id WHERE m.user_id=? ORDER BY h.created_at", CurrentUser.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String, Object> create(@RequestBody Name body) {
    String name = cleanName(body == null ? null : body.name());
    UUID user = CurrentUser.id();
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, user.toString());
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE user_id=?)", Boolean.class, user)))
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    UUID id = UUID.randomUUID();
    try {
      db.update("INSERT INTO household(id,name,owner_id) VALUES(?,?,?)", id, name, user);
      db.update("INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'OWNER')", id, user);
      Invitation invitation = createInvitation(id, null);
      return Map.of("household", Map.of("id", id, "name", name, "role", "OWNER"), "invitation", invitation);
    } catch (DuplicateKeyException exception) {
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    }
  }

  @GetMapping("/{id}/members")
  List<Map<String, Object>> members(@PathVariable UUID id) {
    member(id);
    return db.queryForList("SELECT u.id,u.email,u.display_name,m.role FROM household_member m JOIN app_user u ON u.id=m.user_id WHERE m.household_id=? ORDER BY m.joined_at", id);
  }

  @GetMapping("/{id}/prompt-context")
  Map<String,Object> promptContext(@PathVariable UUID id) {
    member(id);Map<String,Object> out=new LinkedHashMap<>();out.put("household",db.queryForMap("SELECT id,name FROM household WHERE id=?",id));
    List<Map<String,Object>> people=new java.util.ArrayList<>();
    for(Map<String,Object> base:db.queryForList("SELECT u.id,u.display_name,u.email,m.role FROM household_member m JOIN app_user u ON u.id=m.user_id WHERE m.household_id=? ORDER BY m.joined_at",id)){
      UUID user=(UUID)base.get("id");Map<String,Object> person=new LinkedHashMap<>(base);
      person.put("preferences",db.queryForList("SELECT primary_goal,experience_level,activity_level,height_cm,training_days,limitations,biological_sex FROM user_preference WHERE user_id=?",user).stream().findFirst().orElse(Map.of()));
      person.put("nutritionTarget",db.queryForList("SELECT valid_from,calories,protein,carbohydrates,fat,fiber FROM nutrition_target WHERE user_id=? AND valid_from<=CURRENT_DATE ORDER BY valid_from DESC LIMIT 1",user).stream().findFirst().orElse(Map.of()));
      person.put("nutritionPreferences",db.queryForList("SELECT liked_foods,disliked_foods,exclusions,usual_drinks,pantry_staples,cooking_notes,planning_notes,minimize_waste,practical_portions FROM user_nutrition_preference WHERE user_id=?",user).stream().findFirst().orElse(Map.of("minimize_waste",true,"practical_portions",true)));
      person.put("workProfile",db.queryForList("SELECT occupation,work_activity,rotating_shifts,fridge_available,microwave_available,meal_break_minutes,work_notes FROM user_work_profile WHERE user_id=?",user).stream().findFirst().orElse(Map.of()));
      person.put("routineTemplates",db.queryForList("SELECT id,name,work_start,work_end,training_moment,fasted_training,breakfast_location,lunch_location,snack_location,dinner_location,portable_meals,days_of_week,notes FROM daily_routine_template WHERE user_id=? ORDER BY name",user));
      person.put("calendar",db.queryForList("SELECT a.assignment_date,t.name,t.work_start,t.work_end,t.training_moment,t.fasted_training,t.breakfast_location,t.lunch_location,t.snack_location,t.dinner_location,t.portable_meals,a.notes FROM routine_calendar_assignment a JOIN daily_routine_template t ON t.id=a.template_id WHERE a.user_id=? AND a.assignment_date BETWEEN CURRENT_DATE-7 AND CURRENT_DATE+60 ORDER BY a.assignment_date",user));
      person.put("recentBody",db.queryForList("SELECT checkin_date,weight,body_fat_percentage,muscle_mass_kg,visceral_fat_percentage,subcutaneous_fat_percentage,waist_cm,chest_cm,hip_cm,notes FROM body_checkin WHERE user_id=? ORDER BY checkin_date DESC LIMIT 12",user));
      person.put("nutritionPlans",nutritionPlans(user,id));
      person.put("consumedMeals",db.queryForList("SELECT cm.meal_date,cm.meal_type,cm.status,COALESCE(cm.custom_name,pm.meal_name) meal_name,cm.portion,cm.calories,cm.protein,cm.carbohydrates,cm.fat,cm.adherence_percent,cm.deviation_reason,cm.notes,pm.meal_name planned_meal FROM consumed_meal cm LEFT JOIN planned_meal pm ON pm.id=cm.planned_meal_id WHERE cm.user_id=? AND cm.meal_date>=CURRENT_DATE-28 ORDER BY cm.meal_date DESC,cm.completed_at DESC",user));
      person.put("workoutPlans",workoutPlans(user));
      person.put("workoutSessions",workoutSessions(user));
      person.put("sleep",db.queryForList("SELECT sleep_date,total_sleep_minutes,quality_score,morning_energy,bed_time,wake_time,notes FROM sleep_session WHERE user_id=? AND sleep_date>=CURRENT_DATE-28 ORDER BY sleep_date DESC",user));
      person.put("supplements",db.queryForList("SELECT name,dose,schedule,purpose,notes FROM user_supplement WHERE user_id=? AND active=TRUE ORDER BY name",user));
      if("FEMALE".equals(((Map<?,?>)person.get("preferences")).get("biological_sex")))person.put("cycles",db.queryForList("SELECT start_date,end_date,flow_level,symptoms,notes FROM menstrual_cycle_record WHERE user_id=? ORDER BY start_date DESC LIMIT 12",user));
      people.add(person);
    }
    out.put("members",people);
    out.put("pantry",db.queryForList("SELECT i.name,s.quantity,s.unit,s.updated_at FROM household_pantry_stock s JOIN ingredient i ON i.id=s.ingredient_id WHERE s.household_id=? AND s.quantity>0 ORDER BY i.name",id));
    List<Map<String,Object>> travel=db.queryForList("SELECT id,title,start_date,end_date,status,general_guidance FROM nutrition_travel_mode WHERE household_id=? AND end_date>=CURRENT_DATE-28 ORDER BY start_date",id);
    for(Map<String,Object> mode:travel)mode.put("days",db.queryForList("SELECT travel_date,plan_label,guidance FROM nutrition_travel_day WHERE travel_mode_id=? ORDER BY travel_date",mode.get("id")));
    out.put("travelModes",travel);
    out.put("sharingNotice","Al aceptar la unidad doméstica, sus miembros aceptan usar estos datos dentro de ANURA para generar planificación conjunta.");return out;
  }

  private List<Map<String,Object>> nutritionPlans(UUID user,UUID household){
    List<Map<String,Object>> plans=db.queryForList("SELECT DISTINCT p.id,p.name,p.version,p.status,p.valid_from,p.valid_until FROM nutrition_plan p JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id WHERE ump.user_id=? AND (p.owner_id=? OR p.household_id=?) ORDER BY p.version DESC,p.valid_from DESC NULLS LAST LIMIT 2",user,user,household);
    for(Map<String,Object> plan:plans)plan.put("meals",db.queryForList("SELECT d.week_number,d.day_number,d.day_name,pm.meal_order,pm.meal_type,pm.meal_name,r.name recipe,ump.portion_multiplier,ump.quantity,ump.calories,ump.protein,ump.carbohydrates,ump.fat FROM nutrition_plan_day d JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN recipe r ON r.id=pm.recipe_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE d.nutrition_plan_id=? ORDER BY d.week_number,d.day_order,pm.meal_order",user,plan.get("id")));
    return plans;
  }

  private List<Map<String,Object>> workoutPlans(UUID user){
    List<Map<String,Object>> plans=db.queryForList("SELECT id,name,version,status,valid_from,valid_until FROM workout_plan WHERE user_id=? ORDER BY version DESC,valid_from DESC NULLS LAST LIMIT 2",user);
    for(Map<String,Object> plan:plans)plan.put("exercises",db.queryForList("SELECT d.week_number,d.day_number,d.day_name,d.session_name,pe.exercise_order,e.name exercise,e.muscle_group,e.equipment,pe.sets,pe.reps_min,pe.reps_max,pe.target_rir,pe.target_rpe,pe.rest_seconds,pe.tempo,pe.instructions,pe.notes FROM workout_plan_day d JOIN planned_exercise pe ON pe.workout_plan_day_id=d.id JOIN exercise e ON e.id=pe.exercise_id WHERE d.workout_plan_id=? ORDER BY d.week_number,d.day_order,pe.exercise_order",plan.get("id")));
    return plans;
  }

  private List<Map<String,Object>> workoutSessions(UUID user){
    List<Map<String,Object>> sessions=db.queryForList("SELECT id,planned_date,session_name,status,adherence_percent,adherence_reason,duration_seconds,global_rpe,energy_level,pump_level,pain_level,difficulty_level,notes FROM workout_session WHERE user_id=? AND planned_date>=CURRENT_DATE-28 AND deleted_at IS NULL ORDER BY planned_date DESC,started_at DESC",user);
    for(Map<String,Object> session:sessions){
      List<Map<String,Object>> exercises=db.queryForList("SELECT ep.id,ep.exercise_order,e.name exercise,ep.completed_at IS NOT NULL completed,ep.target_sets,ep.target_reps_min,ep.target_reps_max,ep.target_rir,ep.target_rpe,ep.pain_reported,ep.pain_area,ep.pain_intensity,ep.notes FROM exercise_performance ep JOIN exercise e ON e.id=ep.exercise_id WHERE ep.workout_session_id=? ORDER BY ep.exercise_order",session.get("id"));
      for(Map<String,Object> exercise:exercises)exercise.put("sets",db.queryForList("SELECT set_number,set_type,weight,repetitions,rir,rpe,duration_seconds,distance_meters,rest_seconds,tempo,pain_level,completed FROM set_performance WHERE exercise_performance_id=? AND deleted_at IS NULL ORDER BY set_number",exercise.get("id")));
      session.put("exercises",exercises);
    }
    return sessions;
  }

  @PatchMapping("/{id}")
  @Transactional
  Map<String, Object> rename(@PathVariable UUID id, @RequestBody Name body) {
    owner(id);
    String name = cleanName(body == null ? null : body.name());
    db.update("UPDATE household SET name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", name, id);
    return Map.of("id", id, "name", name, "role", "OWNER");
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void leave(@PathVariable UUID id) {
    member(id);
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household WHERE id=? AND owner_id=?)", Boolean.class, id, CurrentUser.id())))
      throw conflict("OWNER_CANNOT_LEAVE", "El propietario debe eliminar la unidad doméstica");
    db.update("DELETE FROM household_member WHERE household_id=? AND user_id=?", id, CurrentUser.id());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void delete(@PathVariable UUID id) {
    owner(id);
    db.update("DELETE FROM shopping_list WHERE household_id=?", id);
    db.update("DELETE FROM nutrition_plan WHERE household_id=?", id);
    db.update("DELETE FROM recipe WHERE household_id=?", id);
    db.update("DELETE FROM ingredient WHERE household_id=?", id);
    db.update("DELETE FROM household WHERE id=?", id);
  }

  @PostMapping("/{id}/invitations")
  @Transactional
  Map<String, Object> invite(@PathVariable UUID id, @RequestBody(required = false) Email body) {
    owner(id);
    String email = body == null || body.email() == null || body.email().isBlank() ? null : body.email().trim().toLowerCase(Locale.ROOT);
    if (email != null) {
      String ownEmail = db.queryForObject("SELECT lower(email) FROM app_user WHERE id=?", String.class, CurrentUser.id());
      if (email.equals(ownEmail)) throw conflict("CANNOT_INVITE_SELF", "No puedes invitarte a ti mismo");
    }
    db.update("UPDATE household_invitation SET status='REVOKED' WHERE household_id=? AND status='PENDING'", id);
    Invitation invitation = createInvitation(id, email);
    String delivery = "NOT_REQUESTED";
    if (email != null && emails.enabled()) {
      try {
        emails.send(email, "Invitación familiar en ANURA", "Usa este código para unirte: " + invitation.code() + "\n\nCaduca en 48 horas.\n" + emails.frontendUrl());
        delivery = "SENT";
      } catch (RuntimeException failure) { delivery = "FAILED"; }
    } else if (email != null) delivery = "EMAIL_DISABLED";
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", invitation.code());
    result.put("expiresAt", invitation.expiresAt());
    result.put("recipientStatus", email == null ? "SHAREABLE_CODE" : registered(email) ? "REGISTERED_USER" : "NEW_USER");
    result.put("deliveryStatus", delivery);
    return result;
  }

  @PostMapping("/invitations/accept")
  @Transactional
  Map<String, Object> accept(@RequestBody Code body) {
    String code = body == null || body.code() == null ? "" : body.code().trim().toUpperCase(Locale.ROOT);
    if (code.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVITATION_CODE_REQUIRED", "Introduce el código de invitación");
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, CurrentUser.id().toString());
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE user_id=?)", Boolean.class, CurrentUser.id())))
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    String userEmail = db.queryForObject("SELECT lower(email) FROM app_user WHERE id=?", String.class, CurrentUser.id());
    for (Map<String, Object> row : db.queryForList("SELECT id,household_id,token_hash,email,expires_at FROM household_invitation WHERE status='PENDING'")) {
      if (!encoder.matches(code, (String) row.get("token_hash"))) continue;
      Instant expires = instant(row.get("expires_at"));
      if (!expires.isAfter(Instant.now())) throw conflict("INVITATION_EXPIRED", "El código de invitación ha caducado");
      if (row.get("email") != null && !userEmail.equalsIgnoreCase(row.get("email").toString()))
        throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH", "La invitación está reservada para otro email");
      UUID household = (UUID) row.get("household_id");
      db.update("INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'MEMBER')", household, CurrentUser.id());
      db.update("UPDATE household_invitation SET status='ACCEPTED',accepted_by=? WHERE id=? AND status='PENDING'", CurrentUser.id(), row.get("id"));
      return Map.of("householdId", household, "role", "MEMBER");
    }
    throw new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Código inválido o ya utilizado");
  }

  private Invitation createInvitation(UUID household, String email) {
    String code = "ANURA-" + segment() + "-" + segment();
    Instant expires = Instant.now().plusSeconds(48 * 60 * 60);
    db.update("INSERT INTO household_invitation(id,household_id,email,token_hash,status,expires_at,invited_by) VALUES(?,?,?,?, 'PENDING',?,?)", UUID.randomUUID(), household, email, encoder.encode(code), java.sql.Timestamp.from(expires), CurrentUser.id());
    return new Invitation(code, expires);
  }

  private static String segment() {
    StringBuilder value = new StringBuilder(4);
    for (int i = 0; i < 4; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    return value.toString();
  }

  private boolean registered(String email) {
    return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE lower(email)=?)", Boolean.class, email));
  }

  private static String cleanName(String value) {
    if (value == null || value.trim().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "HOUSEHOLD_NAME_REQUIRED", "Escribe un nombre para la unidad doméstica");
    String name = value.trim();
    if (name.length() > 160) throw new ApiException(HttpStatus.BAD_REQUEST, "HOUSEHOLD_NAME_TOO_LONG", "El nombre es demasiado largo");
    return name;
  }

  private static Instant instant(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
    return Instant.parse(value.toString());
  }

  private void member(UUID id) {
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE household_id=? AND user_id=?)", Boolean.class, id, CurrentUser.id())))
      throw new ApiException(HttpStatus.FORBIDDEN, "HOUSEHOLD_FORBIDDEN", "Sin acceso a esta unidad doméstica");
  }

  private void owner(UUID id) {
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household WHERE id=? AND owner_id=?)", Boolean.class, id, CurrentUser.id())))
      throw new ApiException(HttpStatus.FORBIDDEN, "HOUSEHOLD_OWNER_REQUIRED", "Solo el propietario puede invitar");
  }

  private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
  record Name(String name) {}
  record Email(String email) {}
  record Code(String code) {}
  record Invitation(String code, Instant expiresAt) {}
}
