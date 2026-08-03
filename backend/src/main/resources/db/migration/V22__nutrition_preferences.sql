CREATE TABLE user_nutrition_preference (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 liked_foods TEXT,
 disliked_foods TEXT,
 exclusions TEXT,
 usual_drinks TEXT,
 pantry_staples TEXT,
 cooking_notes TEXT,
 planning_notes TEXT,
 minimize_waste BOOLEAN NOT NULL DEFAULT TRUE,
 practical_portions BOOLEAN NOT NULL DEFAULT TRUE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
