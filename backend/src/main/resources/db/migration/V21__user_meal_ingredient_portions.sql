CREATE TABLE user_meal_ingredient_portion (
 planned_meal_id UUID NOT NULL REFERENCES planned_meal(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 ingredient_id UUID NOT NULL REFERENCES ingredient(id),
 quantity NUMERIC(12,3) NOT NULL,
 unit VARCHAR(20) NOT NULL,
 PRIMARY KEY(planned_meal_id,user_id,ingredient_id)
);
CREATE INDEX idx_meal_ingredient_portion_user ON user_meal_ingredient_portion(user_id,planned_meal_id);
