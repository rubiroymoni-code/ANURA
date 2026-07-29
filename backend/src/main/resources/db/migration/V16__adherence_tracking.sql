ALTER TABLE consumed_meal DROP CONSTRAINT ck_consumed_meal_status;
ALTER TABLE consumed_meal ADD CONSTRAINT ck_consumed_meal_status CHECK(status IN ('COMPLETED','PARTIAL','SKIPPED','SUBSTITUTED'));
ALTER TABLE consumed_meal ADD COLUMN adherence_percent INTEGER;
ALTER TABLE consumed_meal ADD COLUMN deviation_reason VARCHAR(80);
ALTER TABLE consumed_meal ADD CONSTRAINT ck_meal_adherence CHECK(adherence_percent IS NULL OR adherence_percent BETWEEN 0 AND 100);
UPDATE consumed_meal SET adherence_percent=CASE status WHEN 'COMPLETED' THEN 100 WHEN 'SUBSTITUTED' THEN 85 WHEN 'SKIPPED' THEN 0 END WHERE adherence_percent IS NULL;

ALTER TABLE workout_session ADD COLUMN adherence_reason VARCHAR(120);
