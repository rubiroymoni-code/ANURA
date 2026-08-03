CREATE TABLE nutrition_plan_expiry_notice (
  plan_id UUID NOT NULL REFERENCES nutrition_plan(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id,user_id)
);

