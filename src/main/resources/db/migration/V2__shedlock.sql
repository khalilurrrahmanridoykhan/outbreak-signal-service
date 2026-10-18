-- The lock table used by ShedLock so a scheduled job (the DHIS2 sync) runs on
-- only one instance at a time. Schema as documented by shedlock-provider-jdbc-template.
CREATE TABLE shedlock (
  name       VARCHAR(64)  NOT NULL PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
