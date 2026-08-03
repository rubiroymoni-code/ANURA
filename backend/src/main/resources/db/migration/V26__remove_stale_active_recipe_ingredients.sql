-- Older imports reused recipe codes and accumulated ingredients from previous diets.
-- Exact per-user ingredient portions are the source of truth for the active plan.
DELETE FROM recipe_ingredient ri
WHERE EXISTS (
  SELECT 1
  FROM planned_meal pm
  JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id
  JOIN nutrition_plan p ON p.id=d.nutrition_plan_id
  JOIN user_meal_ingredient_portion exact ON exact.planned_meal_id=pm.id
  WHERE pm.recipe_id=ri.recipe_id AND p.status='ACTIVE'
)
AND NOT EXISTS (
  SELECT 1
  FROM planned_meal pm
  JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id
  JOIN nutrition_plan p ON p.id=d.nutrition_plan_id
  JOIN user_meal_ingredient_portion uip ON uip.planned_meal_id=pm.id
  WHERE pm.recipe_id=ri.recipe_id
    AND p.status='ACTIVE'
    AND uip.ingredient_id=ri.ingredient_id
);
