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
| `fetched_at` | timestamptz | When the row was stored |

Unique on (`series_id`, `week_start`). `week_start` must be a Monday.
