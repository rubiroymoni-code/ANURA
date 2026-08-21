ALTER TABLE exercise_performance
  ADD COLUMN activity_name VARCHAR(160),
  ADD COLUMN activity_minutes INTEGER,
  ADD COLUMN activity_calories NUMERIC(10,2),
  ADD CONSTRAINT ck_exercise_activity_values CHECK(
    (activity_minutes IS NULL OR activity_minutes >= 0)
    AND (activity_calories IS NULL OR activity_calories >= 0)
  );
