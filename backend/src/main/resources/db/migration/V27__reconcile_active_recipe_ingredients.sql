-- A legacy recipe may still contain ingredients inherited from an older import.
-- Per-user meal portions belong to the active plan and are the canonical source.
DELETE FROM recipe_ingredient ri
WHERE EXISTS (
  SELECT 1
  FROM planned_meal pm
  JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id
  JOIN nutrition_plan p ON p.id=d.nutrition_plan_id
  WHERE pm.recipe_id=ri.recipe_id AND p.status='ACTIVE'
)
AND NOT EXISTS (
  SELECT 1
  FROM planned_meal pm
  JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id
  JOIN nutrition_plan p ON p.id=d.nutrition_plan_id
  JOIN user_meal_ingredient_portion exact
    ON exact.planned_meal_id=pm.id
   AND exact.ingredient_id=ri.ingredient_id
  WHERE pm.recipe_id=ri.recipe_id AND p.status='ACTIVE'
);
