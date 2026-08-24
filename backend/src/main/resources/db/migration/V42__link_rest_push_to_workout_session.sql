ALTER TABLE web_push_rest_timer
  ADD COLUMN workout_session_id UUID REFERENCES workout_session(id) ON DELETE CASCADE;

CREATE INDEX idx_web_push_rest_timer_session
  ON web_push_rest_timer(workout_session_id, status);
