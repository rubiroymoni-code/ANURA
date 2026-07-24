CREATE TABLE tracker_entry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    entry_date DATE NOT NULL,
    value NUMERIC(12, 3),
    unit VARCHAR(30),
    details TEXT,
    notes TEXT,
    completed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tracker_entry_type CHECK (type IN ('WORKOUT', 'MEAL', 'WEIGHT', 'MEASUREMENT', 'GOAL'))
);

CREATE INDEX idx_tracker_entry_user_date ON tracker_entry (user_id, entry_date DESC);
CREATE INDEX idx_tracker_entry_user_type ON tracker_entry (user_id, type);
