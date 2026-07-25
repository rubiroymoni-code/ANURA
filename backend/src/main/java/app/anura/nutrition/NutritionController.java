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
        "SELECT DISTINCT p.id,p.name,p.version,p.status,p.valid_from,p.valid_until FROM"
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
            + " EXISTS(SELECT 1 FROM tracker_entry te WHERE te.user_id=? AND te.entry_date=CURRENT_DATE AND te.planned_meal_id=pm.id) completed"
            + " FROM nutrition_plan p LEFT JOIN household_member access ON access.household_id=p.household_id"
            + " JOIN nutrition_plan_day d ON d.nutrition_plan_id=p.id JOIN planned_meal pm ON pm.nutrition_plan_day_id=d.id"
            + " JOIN recipe r ON r.id=pm.recipe_id JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=?"
            + " WHERE p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?) AND d.day_number=?"
            + " ORDER BY pm.meal_order",
        CurrentUser.id(),CurrentUser.id(),CurrentUser.id(),CurrentUser.id(),LocalDate.now().getDayOfWeek().getValue());
  }

  @PostMapping("/today/{mealId}/complete")
  @Transactional
  Map<String,Object> completeTodayMeal(@PathVariable UUID mealId) {
    UUID user=CurrentUser.id();
    Map<String,Object> meal=db.queryForList(
        "SELECT pm.id,pm.meal_name,r.name recipe,ump.calories,ump.protein,ump.carbohydrates,ump.fat"
            + " FROM planned_meal pm JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id JOIN nutrition_plan p ON p.id=d.nutrition_plan_id"
            + " LEFT JOIN household_member access ON access.household_id=p.household_id JOIN recipe r ON r.id=pm.recipe_id"
            + " JOIN user_meal_portion ump ON ump.planned_meal_id=pm.id AND ump.user_id=?"
            + " WHERE pm.id=? AND p.status='ACTIVE' AND (p.owner_id=? OR access.user_id=?)",
        user,mealId,user,user).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"MEAL_NOT_FOUND","Comida planificada no encontrada"));
    UUID entry=UUID.randomUUID();
    db.update("INSERT INTO tracker_entry(id,user_id,type,title,entry_date,value,unit,details,notes,completed,planned_meal_id) VALUES(?,?,'MEAL',?,CURRENT_DATE,?,'kcal',?,? ,TRUE,?) ON CONFLICT(user_id,entry_date,planned_meal_id) WHERE planned_meal_id IS NOT NULL DO NOTHING",
        entry,user,meal.get("meal_name"),meal.get("calories"),meal.get("recipe"),"Comida completada desde el plan",mealId);
    Map<String,Object> result=new LinkedHashMap<>();
    result.put("plannedMealId",mealId);result.put("completed",true);result.put("calories",meal.get("calories"));
    return result;
  }

  @GetMapping("/plans/{id}/week")
  List<Map<String, Object>> week(
      @PathVariable UUID id, @RequestParam(defaultValue = "1") int week) {
    return db.queryForList(
        "SELECT d.day_number,d.day_name,pm.meal_type,pm.meal_name,r.name recipe,u.display_name,"
            + " ump.portion_multiplier,ump.calories,ump.protein,ump.carbohydrates,ump.fat FROM"
            + " nutrition_plan_day d JOIN nutrition_plan p ON p.id=d.nutrition_plan_id LEFT JOIN"
            + " household_member hm ON hm.household_id=p.household_id JOIN planned_meal pm ON"
            + " pm.nutrition_plan_day_id=d.id JOIN recipe r ON r.id=pm.recipe_id JOIN"
            + " user_meal_portion ump ON ump.planned_meal_id=pm.id JOIN app_user u ON"
            + " u.id=ump.user_id WHERE p.id=? AND d.week_number=? AND (p.owner_id=? OR"
            + " hm.user_id=?) ORDER BY d.day_order,pm.meal_order,u.display_name",
        id,
        week,
        CurrentUser.id(),
        CurrentUser.id());
  }

  @GetMapping("/plans/{id}/summary")
  List<Map<String, Object>> summary(
      @PathVariable UUID id, @RequestParam(defaultValue = "1") int week) {
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
            + " 1) nt ON TRUE WHERE p.id=? AND d.week_number=? AND (p.owner_id=? OR"
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
        "SELECT DISTINCT s.id,s.week_number,s.status,s.manually_modified FROM shopping_list s JOIN"
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
                "SELECT p.household_id FROM nutrition_plan p JOIN household_member m ON"
                    + " m.household_id=p.household_id WHERE p.id=? AND m.user_id=?",
                planId,
                CurrentUser.id())
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan no encontrado"));
    UUID household = (UUID) plan.get("household_id");
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
            week);
    int order = 0;
    for (Map<String, Object> row : totals)
      db.update(
          "INSERT INTO"
              + " shopping_list_item(id,shopping_list_id,ingredient_id,name,category,quantity,unit,item_order)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          list,
          row.get("ingredient_id"),
          row.get("name"),
          row.get("category"),
          row.get("quantity"),
          row.get("unit"),
          ++order);
    return Map.of("id", list, "items", totals.size(), "week", week);
  }

  @GetMapping("/shopping-lists/{id}/items")
  List<Map<String, Object>> items(@PathVariable UUID id) {
    return db.queryForList(
        "SELECT i.id,i.name,i.category,i.quantity,i.unit,i.purchased,i.manual FROM"
            + " shopping_list_item i JOIN shopping_list s ON s.id=i.shopping_list_id JOIN"
            + " household_member m ON m.household_id=s.household_id WHERE i.shopping_list_id=? AND"
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
            + " shopping_list_item(id,shopping_list_id,name,category,quantity,unit,manual,item_order)"
            + " VALUES(?,?,?,?,?,?,TRUE,?)",
        item,
        id,
        body.name(),
        body.category(),
        body.quantity(),
        body.unit(),
        order);
    db.update(
        "UPDATE shopping_list SET manually_modified=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        id);
    return Map.of("id", item, "name", body.name());
  }

  @PatchMapping("/shopping-items/{id}/toggle")
  void toggle(@PathVariable UUID id) {
    db.update(
        "UPDATE shopping_list_item i SET purchased=NOT purchased FROM shopping_list"
            + " s,household_member m WHERE i.id=? AND s.id=i.shopping_list_id AND"
            + " m.household_id=s.household_id AND m.user_id=?",
        id,
        CurrentUser.id());
  }
}

record Item(String name, String category, java.math.BigDecimal quantity, String unit) {}
record NutritionTarget(LocalDate validFrom, java.math.BigDecimal calories, java.math.BigDecimal protein, java.math.BigDecimal carbohydrates, java.math.BigDecimal fat, java.math.BigDecimal fiber) {}
