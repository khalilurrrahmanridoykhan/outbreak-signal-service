# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Spring Boot 4.1 project on Java 25 with Maven.
- PostgreSQL schema managed by Flyway: organisation units, weekly series and
  observations, with constraints that reject duplicate weeks, non-Monday week
  starts and negative values.
- Typed, validated settings under `outbreak.*`.
- RFC 9457 problem responses that do not leak internal details.
- Dockerfile and Compose stack (application and PostgreSQL).
- Continuous integration: build and tests, container smoke test, CodeQL,
  dependency review, and a commit-message check.
- Community and governance files, issue forms and a pull request template.
- `EpiWeek`: parses and formats DHIS2 weekly period strings and maps them to
  the Monday that starts each ISO week.
- `DhisClient`/`DhisWebClient`: a DHIS2 Web API client with retry (5xx and
  connection errors only) and a circuit breaker, backed by resilience4j's
  core modules.
- `Dhis2SyncService`: pulls the configured DHIS2 data elements into
  `observation`, idempotently — running it repeatedly with unchanged source
  data leaves the database unchanged, and a corrected value from DHIS2
  overwrites the existing row instead of duplicating it.
- A scheduled sync (`@Scheduled` + ShedLock), configurable by cron expression,
  and a manual `POST /api/v1/sync` trigger.
- `docs/dhis2-spike.md`: findings from the public DHIS2 demo instance that
  shaped this client (district-level org units, explicit weekly period lists,
  and why recent weeks are often empty).
- Five aberration-detection strategies, each hand-verified against
  independently computed fixtures: moving-average z-score, CDC EARS C1/C2/C3,
  and a one-sided CUSUM.
- `DetectionService`: runs every strategy over every series' whole history
  and creates an alert for each alarmed week, idempotently — a re-run over
  unchanged observations creates no new alerts and never touches an existing
  one. Runs as the second step of the scheduled sync.
- `detection_run` and `alert` tables, and a `PackageDependencyTest` that
  enforces detection staying independent of how the data it scores arrived.
- A synthetic-outbreak backtest (`BacktestTest`) measuring sensitivity,
  false-alarm rate and detection delay for every strategy against a fixed
  seed; results in `RESULTS.md`.
- An explicit alert lifecycle state machine (`AlertTransitions`): `OPEN` to
  `ACKNOWLEDGED` to `RESOLVED`, or to `DISMISSED` (reason required) from
  either `OPEN` or `ACKNOWLEDGED`. An illegal move returns `409` naming the
  attempted transition. `alert_event`: an append-only audit trail, one row
  per successful move.
- Optimistic locking on `alert` (`@Version`): a stale concurrent review fails
  with `409` instead of silently overwriting another reviewer's change.
- A REST API over `/api/v1`: paginated, filterable `GET /alerts`,
  `GET /alerts/{id}` with full history, `POST /alerts/{id}/acknowledge`,
  `/resolve` and `/dismiss`, `GET /series`, `GET /series/{id}/observations`,
  and `POST /detection/run`.
- An OAuth2 resource server: every `/api/v1/**` endpoint needs a JWT; roles
  (`VIEWER`, `ANALYST`, `ADMIN`) are read from a Keycloak-shaped
  `realm_access.roles` claim. Reviewing an alert needs `ANALYST` or `ADMIN`;
  triggering a sync or detection run needs `ADMIN`.
- An optional local Keycloak Compose profile with a pre-imported realm and
  three test users, for trying the secured API with a real token — see
  `docs/keycloak.md`.
- Interactive OpenAPI/Swagger UI docs (springdoc), with the bearer scheme
  documented.
- A transactional outbox (`outbox_event`): one row per alert status change,
  written in the same transaction as the change itself, so a notification can
  never be lost for a change that committed, nor sent for one that rolled
  back.
- A two-stage notification pipeline, both stages claiming their work with
  `SELECT ... FOR UPDATE SKIP LOCKED` in small batches so multiple instances
  never double-claim a row: a fan-out poller turns each outbox event into a
  `webhook_delivery` row per active, matching subscription and (if enabled)
  one email; a dispatch poller sends each delivery's signed HTTP POST.
- Webhooks: HMAC-SHA256-signed payloads (`X-Signature`, with a timestamp to
  allow replay rejection) carrying a stable `X-Event-Id` for receiver-side
  deduplication, since delivery is at-least-once, not exactly-once. Exponential
  backoff on failure, then a `DEAD_LETTERED` status after a configured number
  of attempts, visible through `GET /api/v1/webhook-deliveries`.
- `webhook_subscription` CRUD (`/api/v1/webhook-subscriptions`, admin-only): an
  optional per-subscription event-type filter, and a server-generated secret
  if none is supplied, shown once in the create response only.
- Email notifications (Spring Mail to Mailpit in dev): a plain-text message
  per event, sent best-effort after an event's deliveries are fanned out — no
  persisted retry or dead-letter state, unlike webhooks.
- A Mailpit service in `docker-compose.yml` for viewing sent mail locally at
  `http://localhost:8025`.
