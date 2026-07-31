ALTER TABLE user_preference ADD COLUMN biological_sex VARCHAR(20);

CREATE TABLE menstrual_cycle_record (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 start_date DATE NOT NULL,
 end_date DATE,
 flow_level VARCHAR(20),
 symptoms TEXT,
 notes TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_cycle_user_start UNIQUE(user_id,start_date),
 CONSTRAINT ck_cycle_dates CHECK(end_date IS NULL OR end_date>=start_date)
);
CREATE INDEX idx_cycle_user_date ON menstrual_cycle_record(user_id,start_date DESC);
