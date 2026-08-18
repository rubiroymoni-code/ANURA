CREATE TABLE web_push_subscription (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  endpoint TEXT NOT NULL UNIQUE,
  p256dh TEXT NOT NULL,
  auth TEXT NOT NULL,
  device_name VARCHAR(160),
  user_agent TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_success_at TIMESTAMPTZ
);

CREATE INDEX idx_web_push_subscription_user ON web_push_subscription(user_id,enabled);

CREATE TABLE web_push_delivery_log (
  subscription_id UUID NOT NULL REFERENCES web_push_subscription(id) ON DELETE CASCADE,
  reference_key VARCHAR(300) NOT NULL,
  delivered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(subscription_id,reference_key)
);
