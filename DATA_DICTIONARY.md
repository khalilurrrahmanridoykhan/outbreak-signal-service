# Data dictionary

The schema is defined by the Flyway migrations in
`src/main/resources/db/migration`. This file describes what each column means.
It is updated in every pull request that changes the schema.

All data is **aggregate** (counts per place per week). No table holds
patient-level or case-level records.

## `org_unit`

An organisation unit from DHIS2, for example a country, region or district.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `dhis2_uid` | varchar(11), unique | DHIS2 unique id of the unit |
| `name` | varchar(255) | Display name |
| `level` | integer, at least 1 | Depth in the hierarchy (1 is the root) |
| `parent_id` | bigint, nullable | Parent unit; null for the root |

## `series`

One measured quantity for one organisation unit, for example weekly dengue cases
in one district.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `org_unit_id` | bigint | The unit the series belongs to |
| `dhis2_data_element_uid` | varchar(11) | DHIS2 id of the data element measured |
| `name` | varchar(255) | Display name of the quantity |
| `frequency` | varchar(16) | Reporting frequency; only `WEEKLY` is supported |

Unique on (`org_unit_id`, `dhis2_data_element_uid`).

## `observation`

The value of a series for one ISO week.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `series_id` | bigint | The series the value belongs to (deleted with it) |
| `week_start` | date | The **Monday** that starts the ISO week |
| `value` | numeric(14,4), at least 0 | Reported count or indicator value |
| `fetched_at` | timestamptz | When the row was last written (set on both insert and update; see `Observation.updateValue`) |

Unique on (`series_id`, `week_start`). `week_start` must be a Monday.

## `detection_run`

One strategy's execution over every series, for one scheduled or manual
detection cycle. Not referenced by anything except `alert`; exists so every
alert is reproducible from what ran, with what parameters, over how much
history.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `algorithm` | varchar(32) | Which strategy ran (`EARS_C1`, `CUSUM`, ...) |
| `params_json` | text | That strategy's configuration for this run, as JSON |
| `window_weeks` | integer, at least 1 | The configured sync window at the time (not necessarily how much history every series actually had) |
| `started_at` | timestamptz | When the run began |
| `finished_at` | timestamptz, nullable | When it finished; null while still running |
| `series_evaluated` | integer | How many series had at least one observation to evaluate |
| `alerts_created` | integer | How many **new** alerts this run created (an already-existing alert for the same week is not recounted) |

## `alert`

One (series, period, algorithm) that crossed that algorithm's threshold, and
its current reviewed status.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `series_id` | bigint | The series this alert is about (deleted with it) |
| `period_start` | date | The **Monday** that starts the alerted week |
| `algorithm` | varchar(32) | Which strategy raised it |
| `score` | numeric(14,4) | The strategy's statistic for that week |
| `threshold` | numeric(14,4) | The strategy's threshold at the time |
| `severity` | varchar(16) | `LOW`, `MEDIUM` or `HIGH` — see `Severity` |
| `status` | varchar(16) | `OPEN`, `ACKNOWLEDGED`, `RESOLVED` or `DISMISSED`; moves only through the transitions `AlertTransitions` allows |
| `detection_run_id` | bigint | Which run produced it |
| `created_at` | timestamptz | When the alert was created |
| `version` | bigint | Optimistic-locking version; a save against a stale copy fails with `409` rather than overwriting another reviewer's change |

Unique on (`series_id`, `period_start`, `algorithm`) — this is what makes
detection idempotent: re-running it over an unchanged series creates no
second alert for a week already alerted on by the same algorithm.
`period_start` must be a Monday.

## `alert_event`

One status change an alert went through. Append-only: written once per
successful `AlertTransitions` move, in the same transaction as the status
change, never edited or deleted.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint, generated | Internal identifier |
| `alert_id` | bigint | The alert this event belongs to (deleted with it) |
| `from_status` | varchar(16) | The status before this move |
| `to_status` | varchar(16) | The status after this move |
| `note` | text, nullable | The reason, when dismissing; otherwise null |
| `actor` | varchar(255) | Who made the move — the JWT's `sub` claim (a stable id, not the username; see `DECISIONS.md` #30) |
| `created_at` | timestamptz | When the move happened |

## `shedlock`

Not application data — the lock ShedLock uses so the scheduled DHIS2 sync runs
on only one instance at a time (`net.javacrumbs.shedlock`'s own schema).

| Column | Type | Meaning |
|---|---|---|
| `name` | varchar(64), primary key | The lock's name (`dhis2-sync`) |
| `lock_until` | timestamptz | When the lock expires if not released sooner |
| `locked_at` | timestamptz | When the current holder acquired it |
| `locked_by` | varchar(255) | Hostname of the instance holding it |
