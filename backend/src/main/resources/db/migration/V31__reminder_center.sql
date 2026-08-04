CREATE TABLE user_reminder_settings (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  checkin_email BOOLEAN NOT NULL DEFAULT TRUE,
  nutrition_plan_email BOOLEAN NOT NULL DEFAULT TRUE,
  workout_plan_email BOOLEAN NOT NULL DEFAULT TRUE,
  pantry_email BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE custom_reminder (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  title VARCHAR(160) NOT NULL,
  details VARCHAR(500),
  frequency VARCHAR(16) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY')),
  reminder_time TIME NOT NULL,
  day_of_week INTEGER CHECK (day_of_week BETWEEN 1 AND 7),
  email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  next_due_at TIMESTAMPTZ NOT NULL,
  last_sent_at TIMESTAMPTZ,
  last_acknowledged_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_custom_reminder_due ON custom_reminder(enabled,next_due_at);

CREATE TABLE reminder_delivery_log (
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  reminder_type VARCHAR(40) NOT NULL,
  reference_key VARCHAR(160) NOT NULL,
  sent_on DATE NOT NULL DEFAULT CURRENT_DATE,
  PRIMARY KEY(user_id,reminder_type,reference_key,sent_on)
);
