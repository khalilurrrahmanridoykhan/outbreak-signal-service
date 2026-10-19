-- One row per (algorithm, run); detection_run is the audit trail of what ran,
-- with what parameters, over how many series.
CREATE TABLE detection_run (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  algorithm        VARCHAR(32)  NOT NULL,
  params_json      TEXT         NOT NULL,
  window_weeks     INTEGER      NOT NULL,
  started_at       TIMESTAMPTZ  NOT NULL,
  finished_at      TIMESTAMPTZ,
  series_evaluated INTEGER      NOT NULL DEFAULT 0,
  alerts_created   INTEGER      NOT NULL DEFAULT 0,
  CONSTRAINT ck_detection_run_window_weeks CHECK (window_weeks >= 1)
);

CREATE INDEX ix_detection_run_algorithm ON detection_run (algorithm, started_at);

-- One row per (series, period, algorithm) that crossed that algorithm's
-- threshold. The unique constraint is what makes re-running detection over
-- the same history idempotent: inserting an alert that already exists is a
-- no-op (see AlertRepository), so a human's review of an existing alert is
-- never silently overwritten by a later run re-scoring the same week.
CREATE TABLE alert (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  series_id        BIGINT         NOT NULL REFERENCES series (id) ON DELETE CASCADE,
  period_start     DATE           NOT NULL,
  algorithm        VARCHAR(32)    NOT NULL,
  score            NUMERIC(14, 4) NOT NULL,
  threshold        NUMERIC(14, 4) NOT NULL,
  severity         VARCHAR(16)    NOT NULL,
  status           VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
  detection_run_id BIGINT         NOT NULL REFERENCES detection_run (id),
  created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
  CONSTRAINT uq_alert_series_period_algorithm UNIQUE (series_id, period_start, algorithm),
  CONSTRAINT ck_alert_period_start_is_monday CHECK (EXTRACT(ISODOW FROM period_start) = 1),
  CONSTRAINT ck_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
  CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX ix_alert_series_status ON alert (series_id, status);
