ALTER TABLE household_invitation ALTER COLUMN email DROP NOT NULL;

CREATE TABLE password_recovery_code (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  code_hash VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ
);

CREATE INDEX idx_password_recovery_active
  ON password_recovery_code(expires_at)
  WHERE used_at IS NULL;
