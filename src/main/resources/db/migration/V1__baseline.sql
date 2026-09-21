-- Baseline schema: organisation units, the weekly series measured for them,
-- and the observations (one value per series per ISO week).

CREATE TABLE org_unit (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dhis2_uid  VARCHAR(11)  NOT NULL,
  name       VARCHAR(255) NOT NULL,
  level      INTEGER      NOT NULL,
  parent_id  BIGINT       REFERENCES org_unit (id),
  CONSTRAINT uq_org_unit_dhis2_uid UNIQUE (dhis2_uid),
  CONSTRAINT ck_org_unit_level CHECK (level >= 1)
);

CREATE INDEX ix_org_unit_parent ON org_unit (parent_id);

CREATE TABLE series (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  org_unit_id            BIGINT       NOT NULL REFERENCES org_unit (id),
  dhis2_data_element_uid VARCHAR(11)  NOT NULL,
  name                   VARCHAR(255) NOT NULL,
  frequency              VARCHAR(16)  NOT NULL DEFAULT 'WEEKLY',
  CONSTRAINT uq_series_org_unit_data_element UNIQUE (org_unit_id, dhis2_data_element_uid),
  CONSTRAINT ck_series_frequency CHECK (frequency IN ('WEEKLY'))
);

-- week_start is the Monday of the ISO week the value belongs to.
CREATE TABLE observation (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  series_id  BIGINT         NOT NULL REFERENCES series (id) ON DELETE CASCADE,
  week_start DATE           NOT NULL,
  value      NUMERIC(14, 4) NOT NULL,
  fetched_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
  CONSTRAINT uq_observation_series_week UNIQUE (series_id, week_start),
  CONSTRAINT ck_observation_week_start_is_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
  CONSTRAINT ck_observation_value_non_negative CHECK (value >= 0)
);
