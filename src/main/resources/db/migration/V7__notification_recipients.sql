-- Org-unit-scoped email recipients, additive to the single always-notify
-- outbreak.notification.email.to address: a recipient here gets every
-- alert raised for that org unit specifically, on top of whatever the
-- fixed ops address already receives. Managed by admins through
-- /api/v1/notification-recipients, not by editing config.
CREATE TABLE notification_recipient (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  org_unit_id BIGINT       NOT NULL REFERENCES org_unit (id) ON DELETE CASCADE,
  email       VARCHAR(255) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_notification_recipient_org_unit_email UNIQUE (org_unit_id, email)
);

-- AlertEmailNotifier's per-alert lookup is "every recipient for this org unit".
CREATE INDEX ix_notification_recipient_org_unit ON notification_recipient (org_unit_id);
