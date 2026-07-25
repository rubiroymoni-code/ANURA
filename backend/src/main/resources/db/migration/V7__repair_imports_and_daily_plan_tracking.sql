ALTER TABLE import_job ADD COLUMN IF NOT EXISTS household_id UUID REFERENCES household(id);
ALTER TABLE import_job ADD COLUMN IF NOT EXISTS import_scope VARCHAR(30);
ALTER TABLE tracker_entry ADD COLUMN IF NOT EXISTS planned_meal_id UUID REFERENCES planned_meal(id);
CREATE INDEX IF NOT EXISTS idx_import_job_household ON import_job(household_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_import_job_lookup ON import_job(user_id,import_type,checksum,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tracker_daily_planned_meal ON tracker_entry(user_id,entry_date,planned_meal_id) WHERE planned_meal_id IS NOT NULL;
