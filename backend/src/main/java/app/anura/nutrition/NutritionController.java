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
        "SELECT DISTINCT r.id,r.code,r.name,r.servings FROM recipe r LEFT JOIN household_member m"
            + " ON m.household_id=r.household_id WHERE r.owner_id=? OR m.user_id=? ORDER BY r.name",
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
            + " WHERE r.id=? AND (r.owner_id=? OR m.user_id=?) ORDER BY ri.ingredient_order",
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
            + " ump.calories,ump.protein,ump.carbohydrates,ump.fat,ump.portion_multiplier,"
            + " COALESCE(cm.status,'PENDING') status,cm.id consumed_meal_id,cm.custom_name,cm.notes,cm.completed_at"
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
            + " cm.portion,cm.calories,cm.protein,cm.carbohydrates,cm.fat,cm.notes,cm.completed_at,"
            + " pm.meal_name planned_meal,r.name planned_recipe FROM consumed_meal cm"
            + " LEFT JOIN planned_meal pm ON pm.id=cm.planned_meal_id LEFT JOIN recipe r ON r.id=pm.recipe_id"
            + " WHERE cm.user_id=? AND cm.meal_date BETWEEN ? AND ? ORDER BY cm.meal_date,cm.completed_at,cm.meal_type",
        CurrentUser.id(),from,until);
  }

  private Map<String,Object> plannedMeal(UUID mealId){UUID user=CurrentUser.id();return db.queryForList("SELECT pm.id,pm.meal_type,pm.meal_name,ump.calories,ump.protein,ump.carbohydrates,ump.fat FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN household_member access ON access.household_id=p.household_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=? WHERE pm.id=? AND p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?)",user,mealId,user,user).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"MEAL_NOT_FOUND","Comida planificada no encontrada"));}
  private Map<String,Object> savePlanned(UUID mealId,String status,MealInput input,Map<String,Object> meal,String customName){UUID id=UUID.randomUUID();Object calories=input!=null&&input.calories()!=null?input.calories():meal.get("calories");Object protein=input!=null&&input.protein()!=null?input.protein():meal.get("protein");Object carbs=input!=null&&input.carbohydrates()!=null?input.carbohydrates():meal.get("carbohydrates");Object fat=input!=null&&input.fat()!=null?input.fat():meal.get("fat");db.update("INSERT INTO consumed_meal(id,user_id,planned_meal_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at) VALUES(?,?,?,CURRENT_DATE,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(user_id,planned_meal_id,meal_date) WHERE planned_meal_id IS NOT NULL DO UPDATE SET status=EXCLUDED.status,custom_name=EXCLUDED.custom_name,portion=EXCLUDED.portion,calories=EXCLUDED.calories,protein=EXCLUDED.protein,carbohydrates=EXCLUDED.carbohydrates,fat=EXCLUDED.fat,notes=EXCLUDED.notes,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",id,CurrentUser.id(),mealId,normalizeMealType(meal.get("meal_type").toString()),status,customName,input==null?null:clean(input.portion()),"SKIPPED".equals(status)?null:calories,"SKIPPED".equals(status)?null:protein,"SKIPPED".equals(status)?null:carbs,"SKIPPED".equals(status)?null:fat,input==null?null:clean(input.notes()));return db.queryForMap("SELECT id,planned_meal_id,status,custom_name,calories,protein,carbohydrates,fat,notes,completed_at FROM consumed_meal WHERE user_id=? AND planned_meal_id=? AND meal_date=CURRENT_DATE",CurrentUser.id(),mealId);}
  private Map<String,Object> consumed(UUID id){return db.queryForMap("SELECT id,planned_meal_id,meal_date,meal_type,status,custom_name,portion,calories,protein,carbohydrates,fat,notes,completed_at FROM consumed_meal WHERE id=? AND user_id=?",id,CurrentUser.id());}
  private void ownedConsumed(UUID id){if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM consumed_meal WHERE id=? AND user_id=?)",Boolean.class,id,CurrentUser.id())))throw new ApiException(HttpStatus.NOT_FOUND,"CONSUMED_MEAL_NOT_FOUND","Registro de comida no encontrado");}
  private static void validateMealType(String type){if(!Set.of("BREAKFAST","MID_MORNING","LUNCH","SNACK","DINNER","OTHER").contains(type))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_MEAL_TYPE","Selecciona un momento del día válido");}
  private static String normalizeMealType(String value){String v=value.toUpperCase(Locale.ROOT);if(v.contains("DESAY")||v.equals("BREAKFAST"))return"BREAKFAST";if(v.contains("MEDIA")||v.equals("MID_MORNING"))return"MID_MORNING";if(v.equals("COMIDA")||v.equals("LUNCH"))return"LUNCH";if(v.contains("MERIEN")||v.equals("SNACK"))return"SNACK";if(v.contains("CENA")||v.equals("DINNER"))return"DINNER";return"OTHER";}
  private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
  record MealInput(String mealType,String name,LocalDate date,String portion,java.math.BigDecimal calories,java.math.BigDecimal protein,java.math.BigDecimal carbohydrates,java.math.BigDecimal fat,String notes){}

  @GetMapping("/plans/{id}/week")
  List<Map<String, Object>> week(
      @PathVariable UUID id, @RequestParam(required=false) Integer week) {
    return db.queryForList(
        "SELECT d.day_number,d.day_name,pm.meal_type,pm.meal_name,r.name recipe,u.id user_id,u.display_name,"
            + " ump.portion_multiplier,ump.calories,ump.protein,ump.carbohydrates,ump.fat FROM"
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

  @GetMapping("/shopping-lists")
  List<Map<String, Object>> shopping() {
    return db.queryForList(
        "SELECT DISTINCT s.id,s.nutrition_plan_id,s.week_number,s.status,s.manually_modified,s.created_at FROM shopping_list s JOIN"
            + " household_member m ON m.household_id=s.household_id WHERE m.user_id=? ORDER BY"
            + " s.created_at DESC",
        CurrentUser.id());
  }

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
            "SELECT i.id"
                + " ingredient_id,i.name,i.category,ri.unit,SUM(ri.quantity*ump.portion_multiplier)"
                + " quantity FROM nutrition_plan_day d JOIN planned_meal pm ON"
                + " pm.nutrition_plan_day_id=d.id JOIN recipe_ingredient ri ON"
                + " ri.recipe_id=pm.recipe_id JOIN ingredient i ON i.id=ri.ingredient_id JOIN"
                + " user_meal_portion ump ON ump.planned_meal_id=pm.id WHERE d.nutrition_plan_id=?"
                + " AND d.week_number=? GROUP BY i.id,i.name,i.category,ri.unit ORDER BY"
                + " i.category,i.name",
            planId,
            sourceWeek);
    int order = 0;
    for (Map<String, Object> row : totals) {
      java.math.BigDecimal required = (java.math.BigDecimal) row.get("quantity");
      java.math.BigDecimal stock = db.queryForObject(
          "SELECT COALESCE(MAX(quantity),0) FROM household_pantry_stock WHERE household_id=? AND ingredient_id=? AND unit=?",
          java.math.BigDecimal.class, household, row.get("ingredient_id"), row.get("unit"));
      if (stock == null) stock = java.math.BigDecimal.ZERO;
      java.math.BigDecimal pantryUsed = stock.min(required);
      java.math.BigDecimal toBuy = required.subtract(pantryUsed);
      if (pantryUsed.signum() > 0) db.update(
          "UPDATE household_pantry_stock SET quantity=quantity-?,updated_at=CURRENT_TIMESTAMP WHERE household_id=? AND ingredient_id=? AND unit=?",
          pantryUsed, household, row.get("ingredient_id"), row.get("unit"));
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
            + " household_member m ON m.household_id=s.household_id WHERE i.shopping_list_id=? AND i.quantity>0 AND"
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
        body.quantity(),
        body.quantity(),
        body.unit(),
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
record Quantity(java.math.BigDecimal quantity) {}
record NutritionTarget(LocalDate validFrom, java.math.BigDecimal calories, java.math.BigDecimal protein, java.math.BigDecimal carbohydrates, java.math.BigDecimal fat, java.math.BigDecimal fiber) {}
