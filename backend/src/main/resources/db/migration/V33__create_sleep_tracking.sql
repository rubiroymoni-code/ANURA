CREATE TABLE sleep_session (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 sleep_date DATE NOT NULL,
 total_sleep_minutes INTEGER NOT NULL CHECK(total_sleep_minutes BETWEEN 0 AND 1440),
 quality_score SMALLINT CHECK(quality_score IS NULL OR quality_score BETWEEN 1 AND 5),
 morning_energy SMALLINT CHECK(morning_energy IS NULL OR morning_energy BETWEEN 1 AND 5),
 bed_time TIME, wake_time TIME, notes TEXT,
 source VARCHAR(30) NOT NULL DEFAULT 'MANUAL' CHECK(source IN ('MANUAL','HEALTH_CONNECT','HEALTHKIT','IMPORT')),
 source_external_id VARCHAR(150), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_sleep_user_date UNIQUE(user_id,sleep_date)
);
CREATE INDEX idx_sleep_user_date ON sleep_session(user_id,sleep_date DESC);
