ALTER TABLE pos_preferences
  ADD COLUMN notification_email VARCHAR(254) NOT NULL DEFAULT '';

ALTER TABLE notification_email_deliveries
  ADD COLUMN uses_notification_email BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_notification_shared_email
  ON notification_email_deliveries (notification_id)
  WHERE uses_notification_email;

ALTER TABLE daily_summary_deliveries
  ADD COLUMN to_email VARCHAR(254),
  ADD COLUMN uses_notification_email BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_daily_summary_shared_email
  ON daily_summary_deliveries (report_id, batch_key)
  WHERE channel = 'EMAIL' AND uses_notification_email;
