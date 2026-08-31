ALTER TABLE planned_meal ADD COLUMN option_group VARCHAR(120);
ALTER TABLE planned_meal ADD COLUMN option_code VARCHAR(40) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE planned_meal ADD COLUMN option_label VARCHAR(80);
ALTER TABLE planned_meal ADD COLUMN is_default_option BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE planned_meal SET option_group=id::text WHERE option_group IS NULL;
ALTER TABLE planned_meal ALTER COLUMN option_group SET NOT NULL;
ALTER TABLE planned_meal DROP CONSTRAINT IF EXISTS planned_meal_nutrition_plan_day_id_meal_order_key;
CREATE UNIQUE INDEX uk_planned_meal_option ON planned_meal(nutrition_plan_day_id,meal_order,option_code);
CREATE TABLE meal_option_selection (
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  meal_date DATE NOT NULL,
  option_group VARCHAR(120) NOT NULL,
  option_code VARCHAR(40) NOT NULL,
  PRIMARY KEY(user_id,meal_date,option_group)
);
