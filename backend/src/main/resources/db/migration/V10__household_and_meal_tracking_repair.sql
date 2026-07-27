CREATE TABLE consumed_meal (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  planned_meal_id UUID REFERENCES planned_meal(id) ON DELETE SET NULL,
  meal_date DATE NOT NULL,
  meal_type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  custom_name VARCHAR(180),
  portion VARCHAR(120),
  calories NUMERIC(10,2),
  protein NUMERIC(10,2),
  carbohydrates NUMERIC(10,2),
  fat NUMERIC(10,2),
  notes TEXT,
  photo_url TEXT,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_consumed_meal_status CHECK(status IN ('COMPLETED','SKIPPED','SUBSTITUTED')),
  CONSTRAINT ck_consumed_meal_type CHECK(meal_type IN ('BREAKFAST','MID_MORNING','LUNCH','SNACK','DINNER','OTHER'))
);

CREATE UNIQUE INDEX uk_consumed_planned_meal
  ON consumed_meal(user_id, planned_meal_id, meal_date)
  WHERE planned_meal_id IS NOT NULL;
CREATE INDEX idx_consumed_meal_user_date ON consumed_meal(user_id, meal_date DESC);
