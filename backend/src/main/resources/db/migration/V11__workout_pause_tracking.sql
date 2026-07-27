ALTER TABLE workout_session ADD COLUMN paused_at TIMESTAMPTZ;
ALTER TABLE workout_session ADD COLUMN paused_seconds INTEGER NOT NULL DEFAULT 0;
