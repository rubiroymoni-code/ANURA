ALTER TABLE workout_session ADD COLUMN adherence_percent INTEGER;
ALTER TABLE workout_session ADD CONSTRAINT ck_workout_adherence CHECK(adherence_percent IS NULL OR adherence_percent BETWEEN 0 AND 100);
UPDATE workout_session SET adherence_percent=CASE WHEN status='COMPLETED' THEN 100 WHEN status='ABANDONED' THEN 0 END WHERE adherence_percent IS NULL;
