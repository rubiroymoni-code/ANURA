CREATE TABLE user_preference (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 primary_goal VARCHAR(40),
 experience_level VARCHAR(30),
 activity_level VARCHAR(30),
 height_cm NUMERIC(6,2),
 training_days INTEGER,
 limitations TEXT,
 reminder_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 reminder_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
 last_summary_sent_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO user_preference(user_id) SELECT id FROM app_user ON CONFLICT DO NOTHING;
