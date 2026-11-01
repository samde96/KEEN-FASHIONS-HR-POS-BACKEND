ALTER TABLE pos_preferences
  ADD COLUMN notification_email_started_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE notification_email_deliveries (
  id UUID PRIMARY KEY,
  notification_id UUID NOT NULL REFERENCES notifications(id),
  user_id UUID NOT NULL REFERENCES users(id),
  to_email VARCHAR(254) NOT NULL,
  subject VARCHAR(160) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  last_error VARCHAR(250),
  UNIQUE (notification_id, user_id)
);

CREATE INDEX ix_notification_email_due ON notification_email_deliveries (status, next_attempt_at);
