-- Optimistic locking: two analysts acknowledging the same alert at once must
-- not both silently win. JPA's @Version bumps this on every update and
-- rejects a save whose version is stale.
ALTER TABLE alert ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Append-only: every status change an alert goes through, never updated or
-- deleted, so a reviewed alert's history can always be read back in full.
CREATE TABLE alert_event (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  alert_id     BIGINT      NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
  from_status  VARCHAR(16) NOT NULL,
  to_status    VARCHAR(16) NOT NULL,
  note         TEXT,
  actor        VARCHAR(255) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_alert_event_from_status CHECK (from_status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
  CONSTRAINT ck_alert_event_to_status CHECK (to_status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX ix_alert_event_alert ON alert_event (alert_id, created_at);
