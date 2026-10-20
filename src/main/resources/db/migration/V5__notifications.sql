-- The transactional outbox: one row per domain event (an alert opened or
-- changed status), written in the SAME transaction as that alert change so a
-- notification can never be lost for a change that committed, nor sent for
-- one that rolled back. "dispatched" means fan-out to webhook_delivery rows
-- (and the email attempt) has happened, not that every delivery succeeded —
-- each webhook_delivery tracks its own outcome independently.
CREATE TABLE outbox_event (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  alert_id      BIGINT      NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
  event_type    VARCHAR(32) NOT NULL,
  payload       TEXT        NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispatched_at TIMESTAMPTZ,
  CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'DISPATCHED'))
);

-- The poller claims a batch with `SELECT ... FOR UPDATE SKIP LOCKED`; this
-- index keeps that claim query (and the ORDER BY id it uses for fairness)
-- cheap even once old, already-dispatched rows pile up.
CREATE INDEX ix_outbox_event_status ON outbox_event (status, id);

CREATE TABLE webhook_subscription (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  url         VARCHAR(2048) NOT NULL,
  secret      VARCHAR(255)  NOT NULL,
  -- Comma-separated event types this subscription wants; NULL/empty means
  -- every event type. Plain text rather than a join table: the list is
  -- short, rarely changes, and is never queried by individual event type.
  event_types VARCHAR(255),
  active      BOOLEAN       NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- One delivery attempt record per (outbox event, subscription) that existed
-- and was active when that event was fanned out. Independent of every other
-- delivery for the same event, so one slow or failing endpoint never blocks
-- or delays another subscriber.
CREATE TABLE webhook_delivery (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  outbox_event_id    BIGINT      NOT NULL REFERENCES outbox_event (id) ON DELETE CASCADE,
  subscription_id    BIGINT      NOT NULL REFERENCES webhook_subscription (id) ON DELETE CASCADE,
  status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts           INT         NOT NULL DEFAULT 0,
  next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error         TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_webhook_delivery_status CHECK (status IN ('PENDING', 'SENDING', 'DELIVERED', 'DEAD_LETTERED')),
  -- Defense in depth against a double fan-out ever creating two delivery
  -- rows for the same (event, subscription) pair; the poller's own
  -- transactionality is what actually prevents that in practice.
  CONSTRAINT uq_webhook_delivery_event_subscription UNIQUE (outbox_event_id, subscription_id)
);

-- The dispatcher's claim query matches rows that are due (PENDING, past
-- their backoff) or stuck (SENDING past a staleness window, meaning the
-- instance that claimed them crashed mid-send); this index covers both.
CREATE INDEX ix_webhook_delivery_claimable ON webhook_delivery (status, next_attempt_at);
