CREATE TABLE nutrition_target (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), valid_from DATE NOT NULL,
 calories NUMERIC(10,2), protein NUMERIC(10,2), carbohydrates NUMERIC(10,2), fat NUMERIC(10,2), fiber NUMERIC(10,2),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id,valid_from)
);
ALTER TABLE import_job ADD COLUMN household_id UUID REFERENCES household(id);
ALTER TABLE import_job ADD COLUMN import_scope VARCHAR(30);
CREATE INDEX idx_import_job_household ON import_job(household_id,created_at DESC);
