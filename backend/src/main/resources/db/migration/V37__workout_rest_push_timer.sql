CREATE TABLE web_push_rest_timer (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  client_timer_id UUID NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at TIMESTAMPTZ,
  UNIQUE(user_id,client_timer_id)
);

CREATE INDEX idx_web_push_rest_timer_due ON web_push_rest_timer(status,end_at);
