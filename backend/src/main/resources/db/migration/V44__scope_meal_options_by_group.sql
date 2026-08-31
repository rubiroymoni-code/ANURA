DROP INDEX IF EXISTS uk_planned_meal_option;

CREATE UNIQUE INDEX uk_planned_meal_option
  ON planned_meal(nutrition_plan_day_id,meal_order,option_group,option_code);
