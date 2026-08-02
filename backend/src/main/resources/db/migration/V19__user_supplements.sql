CREATE TABLE user_supplement (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(120) NOT NULL,
 dose VARCHAR(80),
 schedule VARCHAR(120),
 purpose VARCHAR(240),
 notes TEXT,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_supplement_user_active ON user_supplement(user_id,active);
