# Outbreak Signal Service

[![CI](https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/actions/workflows/ci.yml/badge.svg)](https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/actions/workflows/ci.yml)
[![CodeQL](https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/actions/workflows/codeql.yml/badge.svg)](https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/actions/workflows/codeql.yml)
[![License](https://img.shields.io/github/license/khalilurrrahmanridoykhan/outbreak-signal-service)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)

Spring Boot service that pulls weekly surveillance data from
[DHIS2](https://dhis2.org), flags statistical aberrations (EARS, CUSUM), and
publishes the resulting alerts as a REST API, signed webhooks, email and FHIR
`DetectedIssue` resources.

> **An alert is a statistical signal for a person to review. It is not a
> confirmed outbreak.** See [ETHICS.md](ETHICS.md) for what this software is,
> what it is not, and its known limits.

## Status

Under active development, built in seven phases. Everything below "Planned"
is not implemented yet.

| Phase | Scope | State |
|---|---|---|
| 1 | Scaffold, PostgreSQL schema, CI, repository standards | **Done** |
| 2 | DHIS2 client and idempotent sync | **Done** |
| 3 | Detection engine (moving-average z, EARS C1-C3, CUSUM) and backtest | **Done** |
| 4 | Alerts API with lifecycle and JWT security | **Done** |
| 5 | Notifications: signed webhooks and email via a transactional outbox | **Done** |
| 6 | FHIR output (`DetectedIssue`, read-only) | Planned |
| 7 | Observability, packaging, `v0.1.0` release | Planned |

Progress is tracked through the milestones and issues of this repository, and
user-visible changes are listed in [CHANGELOG.md](CHANGELOG.md).

## What works today

- Spring Boot 4.1 application on Java 25.
- PostgreSQL schema managed by Flyway: organisation units, weekly series and
  observations, with database constraints that reject duplicate weeks, week
  starts that are not Mondays, and negative values.
- Typed, validated configuration under `outbreak.*`.
- RFC 9457 problem responses that never leak stack traces or SQL.
- Health and readiness probes at `/actuator/health`.
- Container image and a Compose stack.
- A DHIS2 client (retry on 5xx/connection errors only, plus a circuit breaker)
  that pulls weekly surveillance data into `observation`, idempotently — see
  [docs/dhis2-spike.md](docs/dhis2-spike.md) for what that data actually looks
  like on the public demo this defaults to.
- A scheduled sync (daily by default, configurable) coordinated with ShedLock
  so only one running instance ever syncs at a time, plus a manual
  `POST /api/v1/sync` trigger.
- Five aberration-detection strategies (moving-average z-score, CDC EARS
  C1/C2/C3, a one-sided CUSUM), each hand-verified against independently
  computed fixtures, running automatically right after every sync and
  creating an `alert` idempotently — see
  [RESULTS.md](RESULTS.md) for how they actually perform against a synthetic
  outbreak, measured, including where one of them (CUSUM) looks worse on a
  naive metric than it really is.
- A REST API over `/api/v1`: paginated, filterable `GET /alerts`, an alert's
  full reviewed history at `GET /alerts/{id}`, and
  `acknowledge`/`resolve`/`dismiss` enforcing an explicit state machine
  (`409` on an illegal move) with optimistic locking (`409` on a stale
  concurrent review) — plus read-only `GET /series` and
  `GET /series/{id}/observations`.
- An OAuth2 resource server securing all of it: JWT bearer tokens, roles
  read from Keycloak's own `realm_access.roles` claim, reviewing an alert
  needs `ANALYST` or `ADMIN`, triggering a sync or a detection run needs
  `ADMIN`. Interactive API docs at `/swagger-ui/index.html`. See
  [docs/keycloak.md](docs/keycloak.md) to try it with a real token from the
  optional local Keycloak.
- A transactional outbox: every alert status change writes an `outbox_event`
  row in the same transaction as the change itself, fanned out by a poller
  (claiming batches with `SELECT ... FOR UPDATE SKIP LOCKED`, so multiple
  instances never double-claim a row) into a signed webhook delivery per
  active, matching subscription, plus a best-effort email. Webhook delivery
  is at-least-once with retry/backoff and a `DEAD_LETTERED` status after the
  configured attempt limit, visible through the API —
  `webhook_subscription` CRUD at `/api/v1/webhook-subscriptions` (admin-only)
  and delivery visibility at `/api/v1/webhook-deliveries`.

## Quickstart

You need Docker.

```bash
git clone https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service.git
cd outbreak-signal-service
docker compose up --build
```

Then, in another terminal:

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Stop it with `Ctrl+C`, and remove the database volume with
`docker compose down -v`.

`docker compose up` also starts [Mailpit](https://mailpit.axllent.net/), a
local SMTP test server, with every alert event's notification email visible
at `http://localhost:8025` — nothing is ever sent anywhere real.

The Compose file uses fixed local-development credentials. Do not reuse them
anywhere real.

By default, the app is configured to sync from the **public DHIS2 demo
instance** (its own intentionally public `admin`/`district` login — see
[docs/dhis2-spike.md](docs/dhis2-spike.md)), on a daily schedule. Every
`/api/v1/**` endpoint needs a JWT bearer token (`ADMIN` to trigger a sync or
detection run, any role to read, `ANALYST`/`ADMIN` to review an alert); with
no issuer running, `docker compose up`'s plain form always answers `401` on
these, which is expected — bring up the optional local Keycloak instead to
get a real token and try the secured API:

```bash
docker compose --profile keycloak up --build
```

Then see [docs/keycloak.md](docs/keycloak.md) for the three test users and
how to mint a token and call `POST /api/v1/sync` (`ADMIN`, roughly a minute
against the live demo — five data elements, over a hundred weeks each),
`POST /api/v1/detection/run` (`ADMIN`), and the alerts API. Override every
`outbreak.dhis2.*` setting with your own instance's details, and
`outbreak.security.jwk-set-uri` with your own identity provider's, for
anything beyond trying this out.

## API tour

Every call below needs `Authorization: Bearer $TOKEN` — see
[docs/keycloak.md](docs/keycloak.md) for `$TOKEN`. Full interactive docs
(with the bearer scheme already wired up) are at
`http://localhost:8080/swagger-ui/index.html`.

```bash
# Paginated, filterable
curl -H "Authorization: Bearer $TOKEN" \
  "localhost:8080/api/v1/alerts?status=OPEN&algorithm=EARS_C3&page=0&size=20"

# One alert, with its full reviewed history
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/alerts/62

# ANALYST or ADMIN only
curl -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/api/v1/alerts/62/acknowledge
curl -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/api/v1/alerts/62/resolve
curl -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/api/v1/alerts/83/dismiss \
  -H "Content-Type: application/json" -d '{"reason": "Reporting correction, confirmed with district team"}'

# A series and its weekly values
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/series
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/series/1/observations

# Register a webhook (ADMIN only); the response's "secret" is shown once —
# store it, it is needed to verify X-Signature on every delivery
curl -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/api/v1/webhook-subscriptions \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.org/hooks/outbreak-signal"}'

# Delivery attempts, including dead-lettered ones (ANALYST or ADMIN)
curl -H "Authorization: Bearer $TOKEN" \
  "localhost:8080/api/v1/webhook-deliveries?status=DEAD_LETTERED"
```

An illegal move (resolving an alert that was never acknowledged) returns
`409` naming the attempted transition:

```json
{"detail": "Cannot move an alert from OPEN to RESOLVED", "status": 409, "from": "OPEN", "to": "RESOLVED"}
```

## Architecture (target)

```
DHIS2 Web API ──▶ datasource ──▶ observations (PostgreSQL)
                                     │
                                     ▼
                             detection engine        ◀── done (5 strategies)
                       (Strategy beans: EARS, CUSUM, ...)
                                     │
                                     ▼
                       alert service (state machine, audit trail)   ◀── done
                                     │  same transaction
                                     ▼
                                transactional outbox                ◀── done
                                     │  fan-out poller (SKIP LOCKED)
                                     ├──▶ webhook_delivery ──▶ signed webhooks
                                     │      (dispatch poller, SKIP LOCKED,
                                     │       retry/backoff/dead-letter)
                                     └──▶ email (best-effort)

REST /api/v1/**  (JWT, roles)  ◀── done     FHIR /fhir/DetectedIssue (Phase 6)
```

Everything except FHIR output exists so far. Every alert transition
(`acknowledge`/`resolve`/`dismiss`) is enforced by an explicit state machine
and writes an append-only `alert_event` in the same transaction as the
status change; optimistic locking (`@Version`) means a stale concurrent
review fails with `409` rather than silently losing the other reviewer's
change. The reasoning behind each choice is recorded in
[DECISIONS.md](DECISIONS.md).

## Configuration

| Setting | Environment variable | Default | Meaning |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/outbreaksignal` | JDBC URL |
| `spring.datasource.username` | `DB_USERNAME` | `outbreak` | Database user |
| `spring.datasource.password` | `DB_PASSWORD` | `outbreak` (local only) | Database password |
| `outbreak.sync.window-weeks` | `OUTBREAK_SYNC_WINDOW_WEEKS` | `104` | Weeks of history kept in step with the source (8 to 520) |
| `outbreak.sync.cron` | `OUTBREAK_SYNC_CRON` | `0 0 3 * * *` (03:00 UTC daily) | When the scheduled sync runs |
| `outbreak.dhis2.base-url` | `DHIS2_BASE_URL` | the public demo (see [docs/dhis2-spike.md](docs/dhis2-spike.md)) | DHIS2 instance base URL |
| `outbreak.dhis2.username` / `.password` | `DHIS2_USERNAME` / `DHIS2_PASSWORD` | `admin` / `district` (the demo's own public login) | Basic-auth credentials |
| `outbreak.dhis2.org-unit-level` | `DHIS2_ORG_UNIT_LEVEL` | `3` | Hierarchy level synced as this project's "org unit" (district, on the demo) |
| `outbreak.detection.moving-average.baseline-weeks` / `.threshold-k` | `OUTBREAK_DETECTION_MOVING_AVERAGE_*` | `12` / `3.0` | Moving-average z-score baseline length and alarm threshold |
| `outbreak.detection.ears.baseline-weeks` / `.threshold` | `OUTBREAK_DETECTION_EARS_*` | `7` / `3.0` | Shared by EARS C1, C2 and C3 (C2's 2-week guard band is fixed, not configurable) |
| `outbreak.detection.cusum.baseline-weeks` / `.reference-k-sigma` / `.threshold-h-sigma` | `OUTBREAK_DETECTION_CUSUM_*` | `8` / `0.5` / `5.0` | CUSUM's reference window and its slack/threshold, in standard deviations |
| `outbreak.security.jwk-set-uri` | `OUTBREAK_SECURITY_JWK_SET_URI` | the optional local Keycloak's JWK endpoint | Where to validate incoming JWTs' signatures against |
| `outbreak.notification.outbox.poll-interval-ms` / `.batch-size` | `OUTBREAK_NOTIFICATION_OUTBOX_*` | `5000` / `20` | How often the fan-out poller runs, and its batch size |
| `outbreak.notification.webhook.poll-interval-ms` / `.batch-size` | `OUTBREAK_NOTIFICATION_WEBHOOK_*` | `5000` / `20` | How often the dispatch poller runs, and its batch size |
| `outbreak.notification.webhook.max-attempts` | `OUTBREAK_NOTIFICATION_WEBHOOK_MAX_ATTEMPTS` | `5` | Attempts before a delivery is dead-lettered |
| `outbreak.notification.webhook.initial-backoff-ms` / `.backoff-multiplier` | `OUTBREAK_NOTIFICATION_WEBHOOK_*` | `10000` / `3.0` | The first retry's delay, and how much each later one grows by |
| `outbreak.notification.email.enabled` / `.to` / `.from` | `OUTBREAK_NOTIFICATION_EMAIL_*` | `false` (`true` in `docker-compose.yml`) / `ops@example.org` / `outbreak-signal@example.org` | Whether to email a fixed recipient on every event |

The data elements synced (`outbreak.dhis2.data-elements`) are a list in
`application.yml`, not single environment variables; override the whole list
in a deployment's own configuration file.

Invalid values fail at startup with a clear message rather than at first use.

## Data and privacy

The service is built for **aggregate** surveillance counts (a number per place
per week). It has no fields for names, identifiers or any other patient-level
data, and it must not be given any. No real field or patient data is used in
development, tests or documentation. Small counts in small areas can still be
identifying, so anyone deploying this on real data is responsible for suppression
rules that fit their context. Details and limits are in [ETHICS.md](ETHICS.md).

## Data provenance

The repository itself contains no surveillance data; a running instance
fetches it on demand from whichever DHIS2 is configured — the public demo by
default — and never commits it. [DATA_PROVENANCE.md](DATA_PROVENANCE.md)
documents each data source, its terms, and how results can be reproduced.

## Development

You need JDK 25 and Docker. Maven is provided by the wrapper.

```bash
git config core.hooksPath .githooks   # once per clone
make verify                            # build and run all tests (./mvnw verify)
make run                               # start the app and PostgreSQL
```

Run `make help` for the other tasks.

Tests run against a real PostgreSQL started by Testcontainers. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, commit conventions and
Colima notes.

## Project layout

```
src/main/java/.../outbreaksignal
  ├── observation/   organisation units, series, observations, repositories
  ├── datasource/    DhisClient/DhisWebClient, Dhis2SyncService, scheduling
  ├── detection/     strategies, DetectionService, alert lifecycle + REST API
  ├── notification/  transactional outbox, webhook dispatch, email, REST API
  ├── security/      OAuth2 resource server, role mapping, OpenAPI
  ├── config/        typed, validated application settings
  └── common/        error model (RFC 9457), EpiWeek
src/test/.../detection/backtest/   the synthetic-outbreak backtest
src/main/resources/db/migration     Flyway migrations
keycloak/realm-export.json           the optional local Keycloak's realm
docs/dhis2-spike.md                  findings from the public DHIS2 demo
docs/keycloak.md                     trying the secured API with a real token
RESULTS.md                           measured backtest numbers
```

More packages (`notification`, `fhir`) arrive with their phases.

## Documentation

- [DECISIONS.md](DECISIONS.md): architecture decision records
- [DATA_DICTIONARY.md](DATA_DICTIONARY.md): tables and columns
- [DATA_PROVENANCE.md](DATA_PROVENANCE.md): where data comes from
- [RESULTS.md](RESULTS.md): measured backtest numbers, strategy by strategy
- [ETHICS.md](ETHICS.md): responsible use and limits
- [docs/keycloak.md](docs/keycloak.md): trying the secured API with a real token
- [docs/repo-setup.md](docs/repo-setup.md): how the repository settings are managed

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). Report security problems privately as
described in [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).

## Citation

Citation metadata is in [CITATION.cff](CITATION.cff); GitHub shows a "Cite this
repository" button from it.
