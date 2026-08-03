package app.anura.nutrition;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.util.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nutrition")
public class NutritionController {
  private final JdbcTemplate db;

  NutritionController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/preferences")
  Map<String,Object> preferences(){return db.queryForList("SELECT liked_foods,disliked_foods,exclusions,usual_drinks,pantry_staples,cooking_notes,planning_notes,minimize_waste,practical_portions FROM user_nutrition_preference WHERE user_id=?",CurrentUser.id()).stream().findFirst().orElse(Map.of("minimize_waste",true,"practical_portions",true));}

  @PutMapping("/preferences")
  void preferences(@RequestBody NutritionPreference body){db.update("INSERT INTO user_nutrition_preference(user_id,liked_foods,disliked_foods,exclusions,usual_drinks,pantry_staples,cooking_notes,planning_notes,minimize_waste,practical_portions) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET liked_foods=EXCLUDED.liked_foods,disliked_foods=EXCLUDED.disliked_foods,exclusions=EXCLUDED.exclusions,usual_drinks=EXCLUDED.usual_drinks,pantry_staples=EXCLUDED.pantry_staples,cooking_notes=EXCLUDED.cooking_notes,planning_notes=EXCLUDED.planning_notes,minimize_waste=EXCLUDED.minimize_waste,practical_portions=EXCLUDED.practical_portions,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),clean(body.likedFoods()),clean(body.dislikedFoods()),clean(body.exclusions()),clean(body.usualDrinks()),clean(body.pantryStaples()),clean(body.cookingNotes()),clean(body.planningNotes()),body.minimizeWaste(),body.practicalPortions());}

  @GetMapping("/targets")
  List<Map<String, Object>> targets() {
    return db.queryForList("SELECT valid_from,calories,protein,carbohydrates,fat,fiber FROM nutrition_target WHERE user_id=? ORDER BY valid_from DESC", CurrentUser.id());
  }

  @PutMapping("/targets")
  void target(@RequestBody NutritionTarget body) {
    db.update("INSERT INTO nutrition_target(id,user_id,valid_from,calories,protein,carbohydrates,fat,fiber) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(user_id,valid_from) DO UPDATE SET calories=EXCLUDED.calories,protein=EXCLUDED.protein,carbohydrates=EXCLUDED.carbohydrates,fat=EXCLUDED.fat,fiber=EXCLUDED.fiber", UUID.randomUUID(), CurrentUser.id(), body.validFrom(), body.calories(), body.protein(), body.carbohydrates(), body.fat(), body.fiber());
  }

  @GetMapping("/dashboard")
  Map<String,Object> dashboard() {
    UUID user=CurrentUser.id();
    Map<String,Object> target=db.queryForList("SELECT calories,protein,carbohydrates,fat,fiber FROM nutrition_target WHERE user_id=? AND valid_from<=CURRENT_DATE ORDER BY valid_from DESC LIMIT 1",user).stream().findFirst().orElse(Map.of());
    Map<String,Object> planned=db.queryForList("SELECT COALESCE(SUM(ump.calories),0) calories,COALESCE(SUM(ump.protein),0) protein,COALESCE(SUM(ump.carbohydrates),0) carbohydrates,COALESCE(SUM(ump.fat),0) fat FROM nutrition_plan p LEFT JOIN household_member access ON access.household_id=p.household_id JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?) AND d.day_number=?",user,user,user,LocalDate.now().getDayOfWeek().getValue()).getFirst();
    Map<String,Object> consumed=db.queryForMap("SELECT COALESCE(SUM(calories),0) calories,COALESCE(SUM(protein),0) protein,COALESCE(SUM(carbohydrates),0) carbohydrates,COALESCE(SUM(fat),0) fat FROM consumed_meal WHERE user_id=? AND meal_date=CURRENT_DATE AND status IN ('COMPLETED','SUBSTITUTED')",user);
    List<Map<String,Object>> week=db.queryForList("SELECT meal_date date,COALESCE(SUM(calories),0) calories FROM consumed_meal WHERE user_id=? AND meal_date BETWEEN date_trunc('week',CURRENT_DATE)::date AND CURRENT_DATE AND status IN ('COMPLETED','SUBSTITUTED') GROUP BY meal_date ORDER BY meal_date",user);
    Map<String,Object> result=new LinkedHashMap<>();result.put("target",target);result.put("planned",planned);result.put("consumed",consumed);result.put("week",week);return result;
  }

  @GetMapping("/recipes")
  List<Map<String, Object>> recipes() {
    return db.queryForList(
        "SELECT DISTINCT r.id,r.code,r.name,r.servings FROM recipe r JOIN planned_meal pm ON pm.recipe_id=r.id JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN household_member m"
            + " ON m.household_id=p.household_id WHERE p.status='ACTIVE' AND (p.owner_id=? OR m.user_id=?) ORDER BY r.name",
        CurrentUser.id(),
        CurrentUser.id());
  }

  @GetMapping("/recipes/{id}")
  List<Map<String, Object>> recipe(@PathVariable UUID id) {
    return db.queryForList(
        "SELECT r.id,r.name,r.servings,r.instructions,i.name ingredient,ri.quantity,ri.unit,"
            + " i.calories_100,i.protein_100,i.carbohydrates_100,i.fat_100,i.fiber_100 FROM recipe"
            + " r JOIN recipe_ingredient ri ON ri.recipe_id=r.id JOIN ingredient i ON"
            + " i.id=ri.ingredient_id LEFT JOIN household_member m ON m.household_id=r.household_id"
            + " WHERE r.id=? AND (r.owner_id=? OR m.user_id=?) AND EXISTS(SELECT 1 FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id JOIN user_meal_ingredient_portion exact ON exact.planned_meal_id=pm.id AND exact.ingredient_id=ri.ingredient_id WHERE pm.recipe_id=r.id AND p.status='ACTIVE') ORDER BY ri.ingredient_order",
        id,
        CurrentUser.id(),
        CurrentUser.id());
  }

  @GetMapping("/plans")
  List<Map<String, Object>> plans() {
    return db.queryForList(
        "SELECT DISTINCT p.id,p.name,p.version,p.status,p.valid_from,p.valid_until,p.created_at FROM"
            + " nutrition_plan p LEFT JOIN household_member m ON m.household_id=p.household_id"
            + " WHERE p.owner_id=? OR m.user_id=? ORDER BY p.created_at DESC",
        CurrentUser.id(),
        CurrentUser.id());
  }

  @GetMapping("/today")
  List<Map<String, Object>> today() {
    return db.queryForList(
        "SELECT pm.id planned_meal_id,p.id plan_id,p.name plan_name,p.version,d.day_name,pm.meal_type,pm.meal_name,r.name recipe,"
            + " CASE WHEN cm.status='SKIPPED' THEN 0 ELSE COALESCE(cm.calories,ump.calories) END calories,"
            + " CASE WHEN cm.status='SKIPPED' THEN 0 ELSE COALESCE(cm.protein,ump.protein) END protein,"
            + " CASE WHEN cm.status='SKIPPED' THEN 0 ELSE COALESCE(cm.carbohydrates,ump.carbohydrates) END carbohydrates,"
            + " CASE WHEN cm.status='SKIPPED' THEN 0 ELSE COALESCE(cm.fat,ump.fat) END fat,ump.portion_multiplier,"
            + " ump.calories planned_calories,ump.protein planned_protein,ump.carbohydrates planned_carbohydrates,ump.fat planned_fat,"
            + " COALESCE(cm.status,'PENDING') status,cm.id consumed_meal_id,cm.custom_name,cm.portion actual_portion,cm.notes,cm.completed_at"
            + " FROM nutrition_plan p LEFT JOIN household_member access ON access.household_id=p.household_id"
            + " JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id"
            + " JOIN recipe r ON r.id=pm.recipe_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=?"
            + " LEFT JOIN consumed_meal cm ON cm.planned_meal_id=pm.id AND cm.user_id=? AND cm.meal_date=CURRENT_DATE"
            + " WHERE p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?) AND d.day_number=?"
            + " ORDER BY pm.meal_order",
        CurrentUser.id(),CurrentUser.id(),CurrentUser.id(),CurrentUser.id(),LocalDate.now().getDayOfWeek().getValue());
  }

  @PostMapping("/today/{mealId}/complete")
  @Transactional
  Map<String,Object> completeTodayMeal(@PathVariable UUID mealId) {
    Map<String,Object> meal=plannedMeal(mealId);
    return savePlanned(mealId,"COMPLETED",null,meal,null);
  }

  @DeleteMapping("/today/{mealId}/completion")
  @Transactional
  void undoTodayMeal(@PathVariable UUID mealId){
    plannedMeal(mealId);
    db.update("DELETE FROM consumed_meal WHERE user_id=? AND planned_meal_id=? AND meal_date=CURRENT_DATE",CurrentUser.id(),mealId);
  }

  @PostMapping("/today/{mealId}/skip")
  @Transactional
  Map<String,Object> skipTodayMeal(@PathVariable UUID mealId,@RequestBody(required=false) MealInput input) {
    Map<String,Object> meal=plannedMeal(mealId);
    return savePlanned(mealId,"SKIPPED",input,meal,null);
  }

  @PostMapping("/today/{mealId}/substitute")
  @Transactional
  Map<String,Object> substituteTodayMeal(@PathVariable UUID mealId,@RequestBody MealInput input) {
    if(input==null||input.name()==null||input.name().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"MEAL_NAME_REQUIRED","Escribe qué has comido");
    Map<String,Object> meal=plannedMeal(mealId);
    return savePlanned(mealId,"SUBSTITUTED",input,meal,input.name().trim());
  }

  @PostMapping("/today/{mealId}/additional")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String,Object> additionalTodayMeal(@PathVariable UUID mealId,@RequestBody MealInput input) {
    validateMealType(input==null?null:input.mealType());
    if(input.name()==null||input.name().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"MEAL_NAME_REQUIRED","Escribe lo que has comido ademas");
    Map<String,Object> meal=plannedMeal(mealId);
    savePlanned(mealId,"COMPLETED",null,meal,null);
    UUID id=UUID.randomUUID();LocalDate date=input.date()==null?LocalDate.now():input.date();
    db.update("INSERT INTO consumed_meal(id,user_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at) VALUES(?,?,?,?, 'COMPLETED',?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,CurrentUser.id(),date,input.mealType(),input.name().trim(),clean(input.portion()),input.calories(),input.protein(),input.carbohydrates(),input.fat(),clean(input.notes()));
    return consumed(id);
  }

  @PostMapping("/today/{mealId}/partial")
  @Transactional
  Map<String,Object> partialTodayMeal(@PathVariable UUID mealId,@RequestBody PartialMeal input) {
    if(input==null||input.percent()==null||input.percent()<1||input.percent()>99) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ADHERENCE","Indica un porcentaje entre 1 y 99");
    Map<String,Object> meal=plannedMeal(mealId);
    Map<String,Object> saved=savePlanned(mealId,"PARTIAL",new MealInput(null,null,null,input.portion(),null,null,null,null,input.notes()),meal,null);
    db.update("UPDATE consumed_meal SET adherence_percent=?,deviation_reason=?,calories=calories*?/100,protein=protein*?/100,carbohydrates=carbohydrates*?/100,fat=fat*?/100 WHERE user_id=? AND planned_meal_id=? AND meal_date=CURRENT_DATE",input.percent(),clean(input.reason()),input.percent(),input.percent(),input.percent(),input.percent(),CurrentUser.id(),mealId);
    return consumed((UUID)saved.get("id"));
  }

  @GetMapping("/adherence")
  Map<String,Object> adherence(@RequestParam(defaultValue="28") int days) {
    int period=Math.min(Math.max(days,7),365);UUID user=CurrentUser.id();
    Map<String,Object> meals=db.queryForMap("SELECT COUNT(*) FILTER(WHERE status='COMPLETED') completed,COUNT(*) FILTER(WHERE status='SUBSTITUTED') substituted,COUNT(*) FILTER(WHERE status='PARTIAL') partial,COUNT(*) FILTER(WHERE status='SKIPPED') skipped,ROUND(COALESCE(AVG(COALESCE(adherence_percent,CASE status WHEN 'COMPLETED' THEN 100 WHEN 'SUBSTITUTED' THEN 85 ELSE 0 END)),0),1) score FROM consumed_meal WHERE user_id=? AND meal_date>=CURRENT_DATE-?",user,period-1);
    Map<String,Object> workouts=db.queryForMap("SELECT COUNT(*) FILTER(WHERE status='COMPLETED' AND COALESCE(adherence_percent,100)=100) completed,COUNT(*) FILTER(WHERE status='COMPLETED' AND adherence_percent<100) partial,COUNT(*) FILTER(WHERE status='ABANDONED') abandoned,ROUND(COALESCE(AVG(COALESCE(adherence_percent,CASE WHEN status='COMPLETED' THEN 100 WHEN status='ABANDONED' THEN 0 END)),0),1) score FROM workout_session WHERE user_id=? AND planned_date>=CURRENT_DATE-? AND deleted_at IS NULL",user,period-1);
    Integer expectedMeals=db.queryForObject("SELECT COUNT(*) FROM generate_series(CURRENT_DATE-?,CURRENT_DATE,INTERVAL '1 day') AS calendar(day) JOIN nutrition_plan p ON p.status='ACTIVE' JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id AND d.day_number=EXTRACT(ISODOW FROM calendar.day) JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE p.owner_id=? OR EXISTS(SELECT 1 FROM household_member hm WHERE hm.household_id=p.household_id AND hm.user_id=?)",Integer.class,period-1,user,user,user);
    Integer expectedWorkouts=db.queryForObject("SELECT COUNT(*) FROM generate_series(CURRENT_DATE-?,CURRENT_DATE,INTERVAL '1 day') AS calendar(day) JOIN (SELECT DISTINCT d.day_number FROM workout_plan p JOIN workout_plan_day d ON d.workout_plan_id=p.id WHERE p.user_id=? AND p.status='ACTIVE') planned ON planned.day_number=EXTRACT(ISODOW FROM calendar.day)",Integer.class,period-1,user);
    long mealRecorded=((Number)meals.get("completed")).longValue()+((Number)meals.get("substituted")).longValue()+((Number)meals.get("partial")).longValue()+((Number)meals.get("skipped")).longValue();
    long workoutRecorded=((Number)workouts.get("completed")).longValue()+((Number)workouts.get("partial")).longValue()+((Number)workouts.get("abandoned")).longValue();
    int mealExpected=Math.max(expectedMeals==null?0:expectedMeals,(int)mealRecorded),workoutExpected=Math.max(expectedWorkouts==null?0:expectedWorkouts,(int)workoutRecorded);
    java.math.BigDecimal mealPoints=(java.math.BigDecimal)db.queryForObject("SELECT COALESCE(SUM(COALESCE(adherence_percent,CASE status WHEN 'COMPLETED' THEN 100 WHEN 'SUBSTITUTED' THEN 85 ELSE 0 END)),0) FROM consumed_meal WHERE user_id=? AND meal_date>=CURRENT_DATE-?",java.math.BigDecimal.class,user,period-1);
    meals.put("expected",mealExpected);meals.put("missing",Math.max(0,mealExpected-mealRecorded));meals.put("score",mealExpected==0?0:mealPoints.divide(java.math.BigDecimal.valueOf(mealExpected),1,java.math.RoundingMode.HALF_UP));
    java.math.BigDecimal workoutPoints=(java.math.BigDecimal)db.queryForObject("SELECT COALESCE(SUM(COALESCE(adherence_percent,CASE WHEN status='COMPLETED' THEN 100 WHEN status='ABANDONED' THEN 0 END)),0) FROM workout_session WHERE user_id=? AND planned_date>=CURRENT_DATE-? AND deleted_at IS NULL",java.math.BigDecimal.class,user,period-1);
    workouts.put("expected",workoutExpected);workouts.put("missing",Math.max(0,workoutExpected-workoutRecorded));workouts.put("score",workoutExpected==0?0:workoutPoints.divide(java.math.BigDecimal.valueOf(workoutExpected),1,java.math.RoundingMode.HALF_UP));
    List<Map<String,Object>> patterns=db.queryForList("SELECT EXTRACT(ISODOW FROM meal_date)::int day_number,COUNT(*) incidents FROM consumed_meal WHERE user_id=? AND meal_date>=CURRENT_DATE-? AND status IN ('SKIPPED','PARTIAL','SUBSTITUTED') GROUP BY 1 ORDER BY incidents DESC,day_number",user,period-1);
    List<Map<String,Object>> weekly=db.queryForList("SELECT week,ROUND(AVG(meal_score),1) meal_score,ROUND(AVG(workout_score),1) workout_score FROM (SELECT date_trunc('week',meal_date)::date week,COALESCE(adherence_percent,CASE status WHEN 'COMPLETED' THEN 100 WHEN 'SUBSTITUTED' THEN 85 ELSE 0 END) meal_score,NULL::numeric workout_score FROM consumed_meal WHERE user_id=? AND meal_date>=CURRENT_DATE-? UNION ALL SELECT date_trunc('week',planned_date)::date,NULL::numeric,COALESCE(adherence_percent,CASE status WHEN 'COMPLETED' THEN 100 WHEN 'ABANDONED' THEN 0 END) FROM workout_session WHERE user_id=? AND planned_date>=CURRENT_DATE-? AND deleted_at IS NULL) x GROUP BY week ORDER BY week",user,period-1,user,period-1);
    int weekMeals=db.queryForObject("SELECT COUNT(*) FROM generate_series(date_trunc('week',CURRENT_DATE),date_trunc('week',CURRENT_DATE)+INTERVAL '6 days',INTERVAL '1 day') calendar(day) JOIN nutrition_plan p ON p.status='ACTIVE' JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id AND d.day_number=EXTRACT(ISODOW FROM calendar.day) JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE p.owner_id=? OR EXISTS(SELECT 1 FROM household_member hm WHERE hm.household_id=p.household_id AND hm.user_id=?)",Integer.class,user,user,user);
    int weekWorkouts=db.queryForObject("SELECT COUNT(*) FROM (SELECT DISTINCT d.day_number FROM workout_plan p JOIN workout_plan_day d ON d.workout_plan_id=p.id WHERE p.user_id=? AND p.status='ACTIVE') planned",Integer.class,user);
    int completedWeekMeals=db.queryForObject("SELECT COUNT(*) FROM consumed_meal WHERE user_id=? AND planned_meal_id IS NOT NULL AND meal_date BETWEEN date_trunc('week',CURRENT_DATE)::date AND (date_trunc('week',CURRENT_DATE)+INTERVAL '6 days')::date AND status='COMPLETED' AND COALESCE(adherence_percent,100)=100",Integer.class,user);
    int completedWeekWorkouts=db.queryForObject("SELECT COUNT(*) FROM workout_session WHERE user_id=? AND workout_plan_day_id IS NOT NULL AND planned_date BETWEEN date_trunc('week',CURRENT_DATE)::date AND (date_trunc('week',CURRENT_DATE)+INTERVAL '6 days')::date AND status='COMPLETED' AND COALESCE(adherence_percent,100)=100 AND deleted_at IS NULL",Integer.class,user);
    int currentExpected=weekMeals+weekWorkouts,currentCompleted=Math.min(currentExpected,completedWeekMeals+completedWeekWorkouts);Map<String,Object> currentWeek=new LinkedHashMap<>();currentWeek.put("expected",currentExpected);currentWeek.put("completed",currentCompleted);currentWeek.put("score",currentExpected==0?0:Math.round(currentCompleted*1000.0/currentExpected)/10.0);currentWeek.put("complete",currentExpected>0&&currentCompleted==currentExpected);
    List<Map<String,Object>> workoutReasons=db.queryForList("SELECT adherence_reason reason,COUNT(*) incidents FROM workout_session WHERE user_id=? AND planned_date>=CURRENT_DATE-? AND deleted_at IS NULL AND adherence_reason IS NOT NULL AND adherence_reason<>'' GROUP BY adherence_reason ORDER BY incidents DESC",user,period-1);
    Map<String,Object> result=new LinkedHashMap<>();result.put("days",period);result.put("meals",meals);result.put("workouts",workouts);result.put("patterns",patterns);result.put("weekly",weekly);result.put("currentWeek",currentWeek);result.put("workoutReasons",workoutReasons);return result;
  }

  @PostMapping("/meals/custom")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String,Object> customMeal(@RequestBody MealInput input) {
    validateMealType(input==null?null:input.mealType());
    if(input.name()==null||input.name().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"MEAL_NAME_REQUIRED","Escribe qué has comido");
    UUID id=UUID.randomUUID();LocalDate date=input.date()==null?LocalDate.now():input.date();
    db.update("INSERT INTO consumed_meal(id,user_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at) VALUES(?,?,?,?, 'COMPLETED',?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,CurrentUser.id(),date,input.mealType(),input.name().trim(),clean(input.portion()),input.calories(),input.protein(),input.carbohydrates(),input.fat(),clean(input.notes()));
    return consumed(id);
  }

  @PatchMapping("/consumed-meals/{id}")
  @Transactional
  Map<String,Object> editConsumed(@PathVariable UUID id,@RequestBody MealInput input) {
    ownedConsumed(id);validateMealType(input.mealType());
    if(input.name()==null||input.name().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"MEAL_NAME_REQUIRED","Escribe qué has comido");
    db.update("UPDATE consumed_meal SET meal_date=?,meal_type=?,custom_name=?,portion=?,calories=?,protein=?,carbohydrates=?,fat=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",input.date()==null?LocalDate.now():input.date(),input.mealType(),input.name().trim(),clean(input.portion()),input.calories(),input.protein(),input.carbohydrates(),input.fat(),clean(input.notes()),id,CurrentUser.id());
    return consumed(id);
  }

  @DeleteMapping("/consumed-meals/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void deleteConsumed(@PathVariable UUID id){ownedConsumed(id);db.update("DELETE FROM consumed_meal WHERE id=? AND user_id=?",id,CurrentUser.id());}

  @GetMapping("/consumed-meals")
  List<Map<String,Object>> consumedMeals(
      @RequestParam(defaultValue="2000-01-01") LocalDate from,
      @RequestParam(required=false) LocalDate to) {
    LocalDate until=to==null?LocalDate.now():to;
    if(from.isAfter(until)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","El rango de fechas no es valido");
    return db.queryForList(
        "SELECT cm.id,cm.meal_date,cm.meal_type,cm.status,COALESCE(cm.custom_name,pm.meal_name,r.name) name,"
            + " cm.portion,cm.calories,cm.protein,cm.carbohydrates,cm.fat,cm.notes,cm.adherence_percent,cm.deviation_reason,cm.completed_at,"
            + " pm.meal_name planned_meal,r.name planned_recipe FROM consumed_meal cm"
            + " LEFT JOIN planned_meal pm ON pm.id=cm.planned_meal_id LEFT JOIN recipe r ON r.id=pm.recipe_id"
            + " WHERE cm.user_id=? AND cm.meal_date BETWEEN ? AND ? ORDER BY cm.meal_date,cm.completed_at,cm.meal_type",
        CurrentUser.id(),from,until);
  }

  private Map<String,Object> plannedMeal(UUID mealId){UUID user=CurrentUser.id();return db.queryForList("SELECT pm.id,pm.meal_type,pm.meal_name,ump.calories,ump.protein,ump.carbohydrates,ump.fat FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN household_member access ON access.household_id=p.household_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE pm.id=? AND p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?)",user,mealId,user,user).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"MEAL_NOT_FOUND","Comida planificada no encontrada"));}
  private Map<String,Object> savePlanned(UUID mealId,String status,MealInput input,Map<String,Object> meal,String customName){UUID id=UUID.randomUUID();boolean substituted="SUBSTITUTED".equals(status);if(substituted&&(input==null||input.calories()==null))throw new ApiException(HttpStatus.BAD_REQUEST,"SUBSTITUTION_CALORIES_REQUIRED","Indica o estima las calorías de lo que has comido");Object calories=substituted?input.calories():input!=null&&input.calories()!=null?input.calories():meal.get("calories");Object protein=substituted?input.protein():input!=null&&input.protein()!=null?input.protein():meal.get("protein");Object carbs=substituted?input.carbohydrates():input!=null&&input.carbohydrates()!=null?input.carbohydrates():meal.get("carbohydrates");Object fat=substituted?input.fat():input!=null&&input.fat()!=null?input.fat():meal.get("fat");db.update("INSERT INTO consumed_meal(id,user_id,planned_meal_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at) VALUES(?,?,?,CURRENT_DATE,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(user_id,planned_meal_id,meal_date) WHERE planned_meal_id IS NOT NULL DO UPDATE SET status=EXCLUDED.status,custom_name=EXCLUDED.custom_name,portion=EXCLUDED.portion,calories=EXCLUDED.calories,protein=EXCLUDED.protein,carbohydrates=EXCLUDED.carbohydrates,fat=EXCLUDED.fat,notes=EXCLUDED.notes,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",id,CurrentUser.id(),mealId,normalizeMealType(meal.get("meal_type").toString()),status,customName,input==null?null:clean(input.portion()),"SKIPPED".equals(status)?null:calories,"SKIPPED".equals(status)?null:protein,"SKIPPED".equals(status)?null:carbs,"SKIPPED".equals(status)?null:fat,input==null?null:clean(input.notes()));return db.queryForMap("SELECT id,planned_meal_id,status,custom_name,calories,protein,carbohydrates,fat,notes,completed_at FROM consumed_meal WHERE user_id=? AND planned_meal_id=? AND meal_date=CURRENT_DATE",CurrentUser.id(),mealId);}
  private Map<String,Object> consumed(UUID id){return db.queryForMap("SELECT id,planned_meal_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at FROM consumed_meal WHERE id=? AND user_id=?",id,CurrentUser.id());}
  private void ownedConsumed(UUID id){if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM consumed_meal WHERE id=? AND user_id=?)",Boolean.class,id,CurrentUser.id())))throw new ApiException(HttpStatus.NOT_FOUND,"CONSUMED_MEAL_NOT_FOUND","Registro de comida no encontrado");}
  private static void validateMealType(String type){if(!Set.of("BREAKFAST","MID_MORNING","LUNCH","SNACK","DINNER","OTHER").contains(type))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_MEAL_TYPE","Selecciona un momento del día válido");}
  private static String normalizeMealType(String value){String v=value.toUpperCase(Locale.ROOT);if(v.contains("DESAY")||v.equals("BREAKFAST"))return"BREAKFAST";if(v.contains("MEDIA")||v.equals("MID_MORNING"))return"MID_MORNING";if(v.equals("COMIDA")||v.equals("LUNCH"))return"LUNCH";if(v.contains("MERIEN")||v.equals("SNACK"))return"SNACK";if(v.contains("CENA")||v.equals("DINNER"))return"DINNER";return"OTHER";}
  private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
  record NutritionPreference(String likedFoods,String dislikedFoods,String exclusions,String usualDrinks,String pantryStaples,String cookingNotes,String planningNotes,boolean minimizeWaste,boolean practicalPortions){}
  record MealInput(String mealType,String name,LocalDate date,String portion,java.math.BigDecimal calories,java.math.BigDecimal protein,java.math.BigDecimal carbohydrates,java.math.BigDecimal fat,String notes){}
  record PartialMeal(Integer percent,String reason,String portion,String notes){}

  @GetMapping("/plans/{id}/week")
  List<Map<String, Object>> week(
      @PathVariable UUID id, @RequestParam(required=false) Integer week) {
    return db.queryForList(
        "SELECT pm.id planned_meal_id,d.day_number,d.day_name,pm.meal_type,pm.meal_name,r.name recipe,u.id user_id,u.display_name,"
            + " ump.portion_multiplier,COALESCE(ump.quantity,(SELECT SUM(ri.quantity*ump.portion_multiplier) FROM recipe_ingredient ri WHERE ri.recipe_id=r.id)) quantity,ump.calories,ump.protein,ump.carbohydrates,ump.fat,"
            + " CAST(COALESCE((SELECT jsonb_agg(jsonb_build_object('name',i.name,'quantity',x.quantity,'unit',x.unit) ORDER BY i.name) FROM user_meal_ingredient_portion x JOIN ingredient i ON i.id=x.ingredient_id WHERE x.planned_meal_id=pm.id AND x.user_id=u.id),(SELECT jsonb_agg(jsonb_build_object('name',i.name,'quantity',ri.quantity*ump.portion_multiplier,'unit',ri.unit) ORDER BY ri.ingredient_order) FROM recipe_ingredient ri JOIN ingredient i ON i.id=ri.ingredient_id WHERE ri.recipe_id=r.id)) AS TEXT) ingredients FROM"
            + " nutrition_plan_day d JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN"
            + " household_member hm ON hm.household_id=p.household_id JOIN planned_meal pm ON"
            + " pm.nutrition_plan_day_id=d.id JOIN recipe r ON r.id=pm.recipe_id JOIN"
            + " user_meal_portion ump ON ump.planned_meal_id=pm.id JOIN app_user u ON"
            + " u.id=ump.user_id WHERE p.id=? AND d.week_number=COALESCE(?,(SELECT MIN(dx.week_number) FROM nutrition_plan_day dx WHERE dx.nutrition_plan_id=p.id)) AND (p.owner_id=? OR"
            + " hm.user_id=?) ORDER BY d.day_order,pm.meal_order,u.display_name",
        id,
        week,
        CurrentUser.id(),
        CurrentUser.id());
  }

  @GetMapping("/meals/{id}/portions")
  List<Map<String,Object>> mealPortions(@PathVariable UUID id){UUID user=CurrentUser.id();return db.queryForList("SELECT u.id user_id,u.display_name,COALESCE(ump.quantity,(SELECT SUM(ri.quantity*ump.portion_multiplier) FROM recipe_ingredient ri WHERE ri.recipe_id=r.id)) quantity,ump.calories,ump.protein,ump.carbohydrates,ump.fat,CAST(COALESCE((SELECT jsonb_agg(jsonb_build_object('name',i.name,'quantity',x.quantity,'unit',x.unit) ORDER BY i.name) FROM user_meal_ingredient_portion x JOIN ingredient i ON i.id=x.ingredient_id WHERE x.planned_meal_id=pm.id AND x.user_id=u.id),(SELECT jsonb_agg(jsonb_build_object('name',i.name,'quantity',ri.quantity*ump.portion_multiplier,'unit',ri.unit) ORDER BY ri.ingredient_order) FROM recipe_ingredient ri JOIN ingredient i ON i.id=ri.ingredient_id WHERE ri.recipe_id=r.id)) AS TEXT) ingredients FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id JOIN recipe r ON r.id=pm.recipe_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id JOIN app_user u ON u.id=ump.user_id WHERE pm.id=? AND (p.owner_id=? OR EXISTS(SELECT 1 FROM household_member hm WHERE hm.household_id=p.household_id AND hm.user_id=?)) ORDER BY u.display_name",id,user,user);}

  @GetMapping("/plans/{id}/details")
  List<Map<String,Object>> planDetails(@PathVariable UUID id) {
    return db.queryForList(
        "SELECT d.week_number,d.day_number,d.day_name,pm.meal_order,pm.meal_type,pm.meal_name,r.name recipe,"
            + " ump.portion_multiplier,ump.quantity,ump.calories,ump.protein,ump.carbohydrates,ump.fat,u.display_name"
            + " FROM nutrition_plan_day d JOIN nutrition_plan p ON p.id=d.nutrition_plan_id"
            + " JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id JOIN recipe r ON r.id=pm.recipe_id"
            + " JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=?"
            + " JOIN app_user u ON u.id=ump.user_id WHERE p.id=? AND (p.owner_id=? OR EXISTS"
            + " (SELECT 1 FROM household_member access WHERE access.household_id=p.household_id AND access.user_id=?))"
            + " ORDER BY d.week_number,d.day_order,pm.meal_order",
        CurrentUser.id(),id,CurrentUser.id(),CurrentUser.id());
  }

  @GetMapping("/plans/{id}/summary")
  List<Map<String, Object>> summary(
      @PathVariable UUID id, @RequestParam(required=false) Integer week) {
    return db.queryForList(
        "SELECT u.id user_id,u.display_name,d.day_number,ROUND(SUM(ump.calories),2) calories,"
            + " ROUND(SUM(ump.protein),2) protein,ROUND(SUM(ump.carbohydrates),2) carbohydrates,"
            + " ROUND(SUM(ump.fat),2) fat,nt.calories target_calories,"
            + " ROUND(SUM(ump.calories)-COALESCE(nt.calories,0),2) calorie_difference FROM"
            + " nutrition_plan_day d JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN"
            + " household_member access ON access.household_id=p.household_id JOIN planned_meal pm"
            + " ON pm.nutrition_plan_day_id=d.id JOIN user_meal_portion ump ON"
            + " ump.planned_meal_id=pm.id JOIN app_user u ON u.id=ump.user_id LEFT JOIN LATERAL"
            + " (SELECT calories FROM nutrition_target t WHERE t.user_id=u.id AND"
            + " t.valid_from<=COALESCE(p.valid_from,CURRENT_DATE) ORDER BY t.valid_from DESC LIMIT"
            + " 1) nt ON TRUE WHERE p.id=? AND d.week_number=COALESCE(?,(SELECT MIN(dx.week_number) FROM nutrition_plan_day dx WHERE dx.nutrition_plan_id=p.id)) AND (p.owner_id=? OR"
            + " access.user_id=?) GROUP BY u.id,u.display_name,d.day_number,nt.calories ORDER BY"
            + " d.day_number,u.display_name",
        id,
        week,
        CurrentUser.id(),
        CurrentUser.id());
  }

  @PostMapping("/plans/{id}/activate")
  @Transactional
  void activate(@PathVariable UUID id) {
    Map<String, Object> plan =
        db
            .queryForList(
                "SELECT p.owner_id,p.household_id FROM nutrition_plan p LEFT JOIN household_member"
                    + " m ON m.household_id=p.household_id WHERE p.id=? AND (p.owner_id=? OR"
                    + " m.user_id=?)",
                id,
                CurrentUser.id(),
                CurrentUser.id())
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan no encontrado"));
    if (plan.get("household_id") != null)
      db.update(
          "UPDATE nutrition_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP WHERE"
              + " household_id=? AND status='ACTIVE' AND id<>?",
          plan.get("household_id"),
          id);
    else
      db.update(
          "UPDATE nutrition_plan SET status='SUPERSEDED',superseded_at=CURRENT_TIMESTAMP WHERE"
              + " owner_id=? AND status='ACTIVE' AND id<>?",
          CurrentUser.id(),
          id);
    db.update(
        "UPDATE nutrition_plan SET"
            + " status='ACTIVE',activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        id);
  }

  @DeleteMapping("/plans/{id}")
  @Transactional
  void deletePlan(@PathVariable UUID id){
    Map<String,Object> plan=db.queryForList("SELECT p.owner_id,p.household_id,p.external_id,p.version,m.role FROM nutrition_plan p LEFT JOIN household_member m ON m.household_id=p.household_id AND m.user_id=? WHERE p.id=? AND (p.owner_id=? OR m.user_id=?)",CurrentUser.id(),id,CurrentUser.id(),CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","Plan no encontrado"));
    boolean allowed=CurrentUser.id().equals(plan.get("owner_id"))||"OWNER".equals(plan.get("role"));
    if(!allowed)throw new ApiException(HttpStatus.FORBIDDEN,"PLAN_DELETE_FORBIDDEN","Solo el propietario puede eliminar un plan compartido");
    db.update("UPDATE tracker_entry SET planned_meal_id=NULL WHERE planned_meal_id IN (SELECT pm.id FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id WHERE d.nutrition_plan_id=?)",id);
    db.update("DELETE FROM shopping_list WHERE nutrition_plan_id=?",id);
    db.update("DELETE FROM nutrition_plan WHERE id=?",id);
    db.update("DELETE FROM recipe r WHERE r.code LIKE '%__PLAN_%' AND NOT EXISTS(SELECT 1 FROM planned_meal pm WHERE pm.recipe_id=r.id)");
    db.update("DELETE FROM import_job WHERE user_id=? AND import_type IN ('INDIVIDUAL_DIET','SHARED_DIET') AND external_id=? AND plan_version=?",CurrentUser.id(),plan.get("external_id"),plan.get("version"));
  }

  @GetMapping("/shopping-lists")
  List<Map<String, Object>> shopping() {
    return db.queryForList(
        "SELECT DISTINCT s.id,s.nutrition_plan_id,s.week_number,s.status,s.manually_modified,s.created_at,p.name plan_name,p.version plan_version FROM shopping_list s JOIN"
            + " household_member m ON m.household_id=s.household_id JOIN nutrition_plan p ON p.id=s.nutrition_plan_id AND p.status='ACTIVE' WHERE m.user_id=? ORDER BY"
            + " s.created_at DESC",
        CurrentUser.id());
  }

  @DeleteMapping("/shopping-lists/{id}")
  @Transactional
  void resetShoppingList(@PathVariable UUID id) {
    UUID household=db.query("SELECT s.household_id FROM shopping_list s JOIN household_member m ON m.household_id=s.household_id WHERE s.id=? AND m.user_id=?",(r,n)->r.getObject(1,UUID.class),id,CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SHOPPING_LIST_NOT_FOUND","Lista de compra no encontrada"));
    db.update("""
        INSERT INTO household_pantry_stock(household_id,ingredient_id,unit,quantity)
        SELECT ?,ingredient_id,unit,SUM(pantry_used) FROM shopping_list_item
        WHERE shopping_list_id=? AND ingredient_id IS NOT NULL AND pantry_used>0
        GROUP BY ingredient_id,unit
        ON CONFLICT(household_id,ingredient_id,unit) DO UPDATE SET
          quantity=household_pantry_stock.quantity+EXCLUDED.quantity,updated_at=CURRENT_TIMESTAMP
        """,household,id);
    int deleted=db.update("DELETE FROM shopping_list s WHERE s.id=? AND EXISTS(SELECT 1 FROM household_member m WHERE m.household_id=s.household_id AND m.user_id=?)",id,CurrentUser.id());
    if(deleted==0)throw new ApiException(HttpStatus.NOT_FOUND,"SHOPPING_LIST_NOT_FOUND","Lista de compra no encontrada");
  }

  @GetMapping("/pantry")
  List<Map<String,Object>> pantry(){
    return db.queryForList("SELECT s.ingredient_id,i.name,i.category,s.unit,s.quantity,s.updated_at FROM household_pantry_stock s JOIN ingredient i ON i.id=s.ingredient_id JOIN household_member m ON m.household_id=s.household_id WHERE m.user_id=? AND s.quantity>0 ORDER BY i.category,i.name",CurrentUser.id());
  }

  @PostMapping("/pantry")
  @Transactional
  Map<String,Object> addPantry(@RequestBody PantryItem body){
    if(body.name()==null||body.name().isBlank()||body.unit()==null||body.unit().isBlank()||body.quantity()==null||body.quantity().signum()<0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PANTRY_ITEM","Revisa el alimento, la cantidad y la unidad");
    UUID household=db.query("SELECT household_id FROM household_member WHERE user_id=? ORDER BY joined_at LIMIT 1",(r,n)->r.getObject(1,UUID.class),CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"HOUSEHOLD_REQUIRED","Crea o acepta una unidad doméstica"));
    String unit=body.unit().trim().toLowerCase(),name=body.name().trim(),category=body.category()==null||body.category().isBlank()?"OTHER":body.category().trim().toUpperCase();
    java.math.BigDecimal quantity=body.quantity();
    if(unit.equals("kg")){unit="g";quantity=quantity.multiply(java.math.BigDecimal.valueOf(1000));}
    if(unit.equals("l")){unit="ml";quantity=quantity.multiply(java.math.BigDecimal.valueOf(1000));}
    final String normalizedUnit=unit;
    UUID ingredient=db.query("SELECT id FROM ingredient WHERE household_id=? AND lower(trim(name))=lower(?) AND lower(base_unit)=? ORDER BY created_at LIMIT 1",(r,n)->r.getObject(1,UUID.class),household,name,normalizedUnit).stream().findFirst().orElseGet(()->{UUID id=UUID.randomUUID();db.update("INSERT INTO ingredient(id,household_id,code,name,category,base_unit) VALUES(?,?,?,?,?,?)",id,household,"PANTRY_"+id,name,category,normalizedUnit);return id;});
    db.update("INSERT INTO household_pantry_stock(household_id,ingredient_id,unit,quantity) VALUES(?,?,?,?) ON CONFLICT(household_id,ingredient_id,unit) DO UPDATE SET quantity=household_pantry_stock.quantity+EXCLUDED.quantity,updated_at=CURRENT_TIMESTAMP",household,ingredient,normalizedUnit,quantity);
    return Map.of("ingredientId",ingredient,"name",name);
  }

  @PatchMapping("/pantry/{ingredientId}")
  void updatePantry(@PathVariable UUID ingredientId,@RequestBody PantryItem body){
    if(body.quantity()==null||body.quantity().signum()<0||body.unit()==null||body.unit().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PANTRY_QUANTITY","La cantidad no es válida");
    int updated=db.update("UPDATE household_pantry_stock s SET quantity=?,updated_at=CURRENT_TIMESTAMP WHERE s.ingredient_id=? AND s.unit=? AND EXISTS(SELECT 1 FROM household_member m WHERE m.household_id=s.household_id AND m.user_id=?)",body.quantity(),ingredientId,body.unit().trim().toLowerCase(),CurrentUser.id());
    if(updated==0)throw new ApiException(HttpStatus.NOT_FOUND,"PANTRY_ITEM_NOT_FOUND","Alimento no encontrado en despensa");
  }

  @DeleteMapping("/pantry/{ingredientId}")
  void deletePantryItem(@PathVariable UUID ingredientId,@RequestParam String unit){db.update("DELETE FROM household_pantry_stock s WHERE s.ingredient_id=? AND s.unit=? AND EXISTS(SELECT 1 FROM household_member m WHERE m.household_id=s.household_id AND m.user_id=?)",ingredientId,unit,CurrentUser.id());}

  @DeleteMapping("/pantry")
  void clearPantry(){db.update("DELETE FROM household_pantry_stock s WHERE EXISTS(SELECT 1 FROM household_member m WHERE m.household_id=s.household_id AND m.user_id=?)",CurrentUser.id());}

  @PostMapping("/plans/{planId}/shopping-list")
  @Transactional
  Map<String, Object> generateShoppingList(
      @PathVariable UUID planId,
      @RequestParam(defaultValue = "1") int week,
      @RequestParam(defaultValue = "false") boolean replaceModified) {
    Map<String, Object> plan =
        db
            .queryForList(
                "SELECT p.household_id,p.owner_id FROM nutrition_plan p LEFT JOIN household_member m ON"
                    + " m.household_id=p.household_id WHERE p.id=? AND (p.owner_id=? OR m.user_id=?)",
                planId,
                CurrentUser.id(),
                CurrentUser.id())
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan no encontrado"));
    UUID household = (UUID) plan.get("household_id");
    if(household==null) household=db.query("SELECT household_id FROM household_member WHERE user_id=? ORDER BY joined_at LIMIT 1",(r,n)->r.getObject(1,UUID.class),CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"HOUSEHOLD_REQUIRED","Crea una unidad doméstica para compartir y mantener la lista de compra"));
    int sourceWeek=week;
    Integer days=db.queryForObject("SELECT count(*) FROM nutrition_plan_day WHERE nutrition_plan_id=? AND week_number=?",Integer.class,planId,sourceWeek);
    if(days==null||days==0){Integer first=db.queryForObject("SELECT MIN(week_number) FROM nutrition_plan_day WHERE nutrition_plan_id=?",Integer.class,planId);if(first!=null)sourceWeek=first;}
    List<Map<String, Object>> existing =
        db.queryForList(
            "SELECT id,manually_modified FROM shopping_list WHERE household_id=? AND"
                + " nutrition_plan_id=? AND week_number=?",
            household,
            planId,
            week);
    UUID list;
    if (!existing.isEmpty()) {
      list = (UUID) existing.getFirst().get("id");
      if (Boolean.TRUE.equals(existing.getFirst().get("manually_modified")) && !replaceModified)
        throw new ApiException(
            HttpStatus.CONFLICT,
            "SHOPPING_LIST_MODIFIED",
            "La lista fue modificada; confirma su sustitución");
      db.update("""
          INSERT INTO household_pantry_stock(household_id,ingredient_id,unit,quantity)
          SELECT ?,ingredient_id,unit,SUM(pantry_used) FROM shopping_list_item
          WHERE shopping_list_id=? AND ingredient_id IS NOT NULL GROUP BY ingredient_id,unit
          ON CONFLICT(household_id,ingredient_id,unit) DO UPDATE SET quantity=household_pantry_stock.quantity+EXCLUDED.quantity,updated_at=CURRENT_TIMESTAMP
          """, household, list);
      db.update("DELETE FROM shopping_list_item WHERE shopping_list_id=?", list);
      db.update(
          "UPDATE shopping_list SET manually_modified=FALSE,updated_at=CURRENT_TIMESTAMP WHERE"
              + " id=?",
          list);
    } else {
      list = UUID.randomUUID();
      db.update(
          "INSERT INTO shopping_list(id,household_id,nutrition_plan_id,week_number,status)"
              + " VALUES(?,?,?,?,'OPEN')",
          list,
          household,
          planId,
          week);
    }
    List<Map<String, Object>> totals =
        db.queryForList(
            """
            WITH portions AS (
              SELECT i.id,i.name,i.category,uip.unit,uip.quantity
              FROM nutrition_plan_day d
              JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id
              JOIN user_meal_ingredient_portion uip ON uip.planned_meal_id=pm.id
              JOIN ingredient i ON i.id=uip.ingredient_id
              WHERE d.nutrition_plan_id=? AND d.week_number=?
              UNION ALL
              SELECT i.id,i.name,i.category,ri.unit,ri.quantity*ump.portion_multiplier
              FROM nutrition_plan_day d
              JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id
              JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id
              JOIN recipe_ingredient ri ON ri.recipe_id=pm.recipe_id
              JOIN ingredient i ON i.id=ri.ingredient_id
              WHERE d.nutrition_plan_id=? AND d.week_number=?
                AND NOT EXISTS (
                  SELECT 1 FROM user_meal_ingredient_portion exact
                  WHERE exact.planned_meal_id=pm.id AND exact.user_id=ump.user_id
                )
            ), raw AS (
              SELECT id,name,
                CASE
                  WHEN upper(category) IN ('CARNE','CARNES','PESCADO','PESCADOS','MEAT_FISH')
                    OR lower(name) ~ '(pollo|pavo|ternera|cerdo|pechuga|pescado|salmón|salmon|merluza|atún|atun)' THEN 'MEAT_FISH'
                  WHEN upper(category) IN ('FRUTA','VERDURA','FRUIT','VEGETABLES','FRUIT_VEGETABLES') THEN 'FRUIT_VEGETABLES'
                  WHEN upper(category) IN ('FRUTA_GRASA','TUBERCULO','TUBÉRCULO') THEN 'FRUIT_VEGETABLES'
                  WHEN upper(category) IN ('HUEVO','HUEVOS','EGG','EGGS') THEN 'EGGS'
                  WHEN upper(category) IN ('LACTEO','LACTEOS','LÁCTEO','LÁCTEOS','DAIRY') THEN 'DAIRY'
                  WHEN upper(category) IN ('CEREAL','CEREALES','LEGUMBRE','LEGUMBRES','CEREALS_LEGUMES') THEN 'CEREALS_LEGUMES'
                  WHEN upper(category) IN ('BEBIDA','BEBIDAS','DRINKS') THEN 'DRINKS'
                  WHEN upper(category) IN ('DESPENSA','PANTRY') THEN 'PANTRY'
                  ELSE upper(category)
                END category,
                CASE
                  WHEN lower(name) ~ '(^| )huevos?( entero)?($| )' AND lower(unit) IN ('mg','g','kg') THEN 'ud'
                  WHEN lower(unit) IN ('l','ml') OR
                    (lower(unit) IN ('mg','g','kg') AND
                      (upper(category) IN ('DRINKS','BEBIDA','BEBIDAS') OR
                       lower(name) ~ '(^| )(leche|agua|zumo|bebida)')) THEN 'ml'
                  WHEN lower(unit) IN ('mg','kg','g') THEN 'g'
                  WHEN lower(unit) IN ('ud','uds','unidad','unidades') THEN 'ud'
                  ELSE lower(unit)
                END unit,
                quantity*CASE
                  WHEN lower(name) ~ '(^| )huevos?( entero)?($| )' AND lower(unit)='mg' THEN 1.0/60000
                  WHEN lower(name) ~ '(^| )huevos?( entero)?($| )' AND lower(unit)='g' THEN 1.0/60
                  WHEN lower(name) ~ '(^| )huevos?( entero)?($| )' AND lower(unit)='kg' THEN 1000.0/60
                  WHEN lower(unit) IN ('kg','l') THEN 1000
                  WHEN lower(unit)='mg' THEN 0.001
                  ELSE 1
                END quantity,
                lower(name) ~ '(^| )huevos?( entero)?($| )' AND lower(unit) IN ('mg','g','kg') round_units
              FROM portions
            )
            SELECT (array_agg(id ORDER BY id::text))[1] ingredient_id,
              MIN(name) name,MIN(category) category,unit,
              CASE WHEN bool_or(round_units) THEN CEIL(SUM(quantity)) ELSE SUM(quantity) END quantity
            FROM raw
            GROUP BY lower(trim(name)),unit
            ORDER BY MIN(category),MIN(name)
            """,
            planId,
            sourceWeek,
            planId,
            sourceWeek);
    int order = 0;
    for (Map<String, Object> row : totals) {
      java.math.BigDecimal required = (java.math.BigDecimal) row.get("quantity");
      List<Map<String,Object>> stockRows=db.queryForList("SELECT s.ingredient_id,s.unit stock_unit,s.quantity original_quantity,CASE WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='mg' THEN 1.0/60000 WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='g' THEN 1.0/60 WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='kg' THEN 1000.0/60 WHEN lower(s.unit) IN ('kg','l') THEN 1000 WHEN lower(s.unit)='mg' THEN 0.001 ELSE 1 END conversion_factor,s.quantity*CASE WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='mg' THEN 1.0/60000 WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='g' THEN 1.0/60 WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit)='kg' THEN 1000.0/60 WHEN lower(s.unit) IN ('kg','l') THEN 1000 WHEN lower(s.unit)='mg' THEN 0.001 ELSE 1 END quantity FROM household_pantry_stock s JOIN ingredient i ON i.id=s.ingredient_id WHERE s.household_id=? AND lower(trim(i.name))=lower(trim(?)) AND CASE WHEN lower(i.name) ~ '(^| )huevos?( entero)?($| )' AND lower(s.unit) IN ('mg','g','kg') THEN 'ud' WHEN lower(s.unit) IN ('l','ml') OR (lower(s.unit) IN ('mg','g','kg') AND (upper(i.category) IN ('DRINKS','BEBIDA','BEBIDAS') OR lower(i.name) ~ '(^| )(leche|agua|zumo|bebida)')) THEN 'ml' WHEN lower(s.unit) IN ('mg','kg','g') THEN 'g' WHEN lower(s.unit) IN ('ud','uds','unidad','unidades') THEN 'ud' ELSE lower(s.unit) END=? AND s.quantity>0 ORDER BY s.updated_at",household,row.get("name"),row.get("unit"));
      java.math.BigDecimal stock=stockRows.stream().map(value->(java.math.BigDecimal)value.get("quantity")).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
      if (stock == null) stock = java.math.BigDecimal.ZERO;
      java.math.BigDecimal pantryUsed = stock.min(required);
      java.math.BigDecimal toBuy = required.subtract(pantryUsed);
      java.math.BigDecimal remainingUse=pantryUsed;
      for(Map<String,Object> stockRow:stockRows){if(remainingUse.signum()<=0)break;java.math.BigDecimal available=(java.math.BigDecimal)stockRow.get("quantity"),used=available.min(remainingUse),factor=(java.math.BigDecimal)stockRow.get("conversion_factor");db.update("UPDATE household_pantry_stock SET quantity=GREATEST(0,quantity-?),updated_at=CURRENT_TIMESTAMP WHERE household_id=? AND ingredient_id=? AND unit=?",used.divide(factor,6,java.math.RoundingMode.HALF_UP),household,stockRow.get("ingredient_id"),stockRow.get("stock_unit"));remainingUse=remainingUse.subtract(used);}
      db.update(
          "INSERT INTO"
              + " shopping_list_item(id,shopping_list_id,ingredient_id,name,category,quantity,required_quantity,pantry_used,unit,item_order)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          list,
          row.get("ingredient_id"),
          row.get("name"),
          row.get("category"),
          toBuy,
          required,
          pantryUsed,
          row.get("unit"),
          ++order);
    }
    return Map.of("id", list, "items", totals.size(), "week", week);
  }

  @GetMapping("/shopping-lists/{id}/items")
  List<Map<String, Object>> items(@PathVariable UUID id) {
    return db.queryForList(
        "SELECT i.id,i.name,i.category,i.quantity,i.required_quantity,i.pantry_used,i.unit,i.purchased,i.manual FROM"
            + " shopping_list_item i JOIN shopping_list s ON s.id=i.shopping_list_id JOIN"
            + " household_member m ON m.household_id=s.household_id WHERE i.shopping_list_id=? AND COALESCE(i.required_quantity,i.quantity)>0 AND"
            + " m.user_id=? ORDER BY i.item_order",
        id,
        CurrentUser.id());
  }

  @PostMapping("/shopping-lists/{id}/items")
  Map<String, Object> add(@PathVariable UUID id, @RequestBody Item body) {
    Integer allowed =
        db.queryForObject(
            "SELECT count(*) FROM shopping_list s JOIN household_member m ON"
                + " m.household_id=s.household_id WHERE s.id=? AND m.user_id=?",
            Integer.class,
            id,
            CurrentUser.id());
    if (allowed == null || allowed == 0)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
    UUID item = UUID.randomUUID();
    String itemUnit=body.unit()==null?"ud":body.unit().trim().toLowerCase();
    java.math.BigDecimal itemQuantity=body.quantity();
    if(itemUnit.equals("kg")){itemUnit="g";itemQuantity=itemQuantity.multiply(java.math.BigDecimal.valueOf(1000));}
    if(itemUnit.equals("l")){itemUnit="ml";itemQuantity=itemQuantity.multiply(java.math.BigDecimal.valueOf(1000));}
    Integer order =
        db.queryForObject(
            "SELECT coalesce(max(item_order),0)+1 FROM shopping_list_item WHERE shopping_list_id=?",
            Integer.class,
            id);
    db.update(
        "INSERT INTO"
            + " shopping_list_item(id,shopping_list_id,name,category,quantity,required_quantity,unit,manual,item_order)"
            + " VALUES(?,?,?,?,?,?,?,TRUE,?)",
        item,
        id,
        body.name(),
        body.category(),
        itemQuantity,
        itemQuantity,
        itemUnit,
        order);
    db.update(
        "UPDATE shopping_list SET manually_modified=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        id);
    return Map.of("id", item, "name", body.name());
  }

  @PatchMapping("/shopping-items/{id}/toggle")
  @Transactional
  void toggle(@PathVariable UUID id) {
    Map<String,Object> item=db.queryForList("SELECT i.purchased,i.quantity,i.required_quantity,i.pantry_used,i.ingredient_id,i.unit,s.household_id FROM shopping_list_item i JOIN shopping_list s ON s.id=i.shopping_list_id JOIN household_member m ON m.household_id=s.household_id WHERE i.id=? AND m.user_id=?",id,CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","Artículo no encontrado"));
    boolean purchased=Boolean.TRUE.equals(item.get("purchased"));
    java.math.BigDecimal bought=(java.math.BigDecimal)item.get("quantity"),required=(java.math.BigDecimal)item.get("required_quantity"),used=(java.math.BigDecimal)item.get("pantry_used");
    if(required==null)required=bought;if(used==null)used=java.math.BigDecimal.ZERO;
    java.math.BigDecimal surplus=bought.add(used).subtract(required).max(java.math.BigDecimal.ZERO);
    if(item.get("ingredient_id")!=null&&surplus.signum()>0){
      if(!purchased) db.update("INSERT INTO household_pantry_stock(household_id,ingredient_id,unit,quantity) VALUES(?,?,?,?) ON CONFLICT(household_id,ingredient_id,unit) DO UPDATE SET quantity=household_pantry_stock.quantity+EXCLUDED.quantity,updated_at=CURRENT_TIMESTAMP",item.get("household_id"),item.get("ingredient_id"),item.get("unit"),surplus);
      else db.update("UPDATE household_pantry_stock SET quantity=GREATEST(0,quantity-?),updated_at=CURRENT_TIMESTAMP WHERE household_id=? AND ingredient_id=? AND unit=?",surplus,item.get("household_id"),item.get("ingredient_id"),item.get("unit"));
    }
    db.update("UPDATE shopping_list_item SET purchased=? WHERE id=?",!purchased,id);
  }

  @PatchMapping("/shopping-items/{id}/quantity")
  @Transactional
  void quantity(@PathVariable UUID id,@RequestBody Quantity body){
    if(body.quantity()==null||body.quantity().signum()<0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUANTITY","Cantidad no válida");
    Map<String,Object> item=db.queryForList("SELECT i.purchased,i.quantity,i.required_quantity,i.pantry_used,i.ingredient_id,i.unit,s.household_id FROM shopping_list_item i JOIN shopping_list s ON s.id=i.shopping_list_id JOIN household_member m ON m.household_id=s.household_id WHERE i.id=? AND m.user_id=?",id,CurrentUser.id()).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","Artículo no encontrado"));
    if(Boolean.TRUE.equals(item.get("purchased"))&&item.get("ingredient_id")!=null){
      java.math.BigDecimal old=((java.math.BigDecimal)item.get("quantity")).add((java.math.BigDecimal)item.get("pantry_used")).subtract((java.math.BigDecimal)item.get("required_quantity")).max(java.math.BigDecimal.ZERO);
      java.math.BigDecimal next=body.quantity().add((java.math.BigDecimal)item.get("pantry_used")).subtract((java.math.BigDecimal)item.get("required_quantity")).max(java.math.BigDecimal.ZERO);
      db.update("UPDATE household_pantry_stock SET quantity=GREATEST(0,quantity+?),updated_at=CURRENT_TIMESTAMP WHERE household_id=? AND ingredient_id=? AND unit=?",next.subtract(old),item.get("household_id"),item.get("ingredient_id"),item.get("unit"));
    }
    db.update("UPDATE shopping_list_item SET quantity=? WHERE id=?",body.quantity(),id);
    db.update("UPDATE shopping_list SET manually_modified=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=(SELECT shopping_list_id FROM shopping_list_item WHERE id=?)",id);
  }
}

record Item(String name, String category, java.math.BigDecimal quantity, String unit) {}
record PantryItem(String name,String category,java.math.BigDecimal quantity,String unit) {}
record Quantity(java.math.BigDecimal quantity) {}
record NutritionTarget(LocalDate validFrom, java.math.BigDecimal calories, java.math.BigDecimal protein, java.math.BigDecimal carbohydrates, java.math.BigDecimal fat, java.math.BigDecimal fiber) {}
