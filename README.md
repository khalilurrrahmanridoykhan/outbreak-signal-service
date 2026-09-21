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

Under active development, built in seven phases. Only the foundation exists so
far; everything below "Planned" is not implemented yet.

| Phase | Scope | State |
|---|---|---|
| 1 | Scaffold, PostgreSQL schema, CI, repository standards | **Done** |
| 2 | DHIS2 client and idempotent sync | Planned |
| 3 | Detection engine (moving-average z, EARS C1-C3, CUSUM) and backtest | Planned |
| 4 | Alerts API with lifecycle and JWT security | Planned |
| 5 | Notifications: signed webhooks and email via a transactional outbox | Planned |
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

The Compose file uses fixed local-development credentials. Do not reuse them
anywhere real.

## Architecture (target)

```
DHIS2 Web API ──▶ datasource ──▶ observations (PostgreSQL)
                                     │
                                     ▼
                             detection engine
                       (Strategy beans: EARS, CUSUM, ...)
                                     │
                                     ▼
                       alert service (state machine, audit trail)
                                     │  same transaction
                                     ▼
                                transactional outbox
                                     ├──▶ signed webhooks
                                     └──▶ email

REST /api/v1/**  (JWT, roles)        FHIR /fhir/DetectedIssue (read-only)
```

Only the persistence layer in this diagram exists so far. The reasoning behind
each choice is recorded in [DECISIONS.md](DECISIONS.md).

## Configuration

| Setting | Environment variable | Default | Meaning |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/outbreaksignal` | JDBC URL |
| `spring.datasource.username` | `DB_USERNAME` | `outbreak` | Database user |
| `spring.datasource.password` | `DB_PASSWORD` | `outbreak` (local only) | Database password |
| `outbreak.sync.window-weeks` | `OUTBREAK_SYNC_WINDOWWEEKS` | `104` | Weeks of history kept in step with the source (8 to 520) |

Invalid values fail at startup with a clear message rather than at first use.

## Development

You need JDK 25 and Docker. Maven is provided by the wrapper.

```bash
git config core.hooksPath .githooks   # once per clone
./mvnw verify                          # build and run all tests
```

Tests run against a real PostgreSQL started by Testcontainers. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, commit conventions and
Colima notes.

## Project layout

```
src/main/java/.../outbreaksignal
  ├── observation/   organisation units, series, observations, repositories
  ├── config/        typed, validated application settings
  └── common/        error model (RFC 9457)
src/main/resources/db/migration   Flyway migrations
```

More packages (`datasource`, `detection`, `alert`, `notification`, `fhir`,
`security`) arrive with their phases.

## Documentation

- [DECISIONS.md](DECISIONS.md): architecture decision records
- [DATA_DICTIONARY.md](DATA_DICTIONARY.md): tables and columns
- [DATA_PROVENANCE.md](DATA_PROVENANCE.md): where data comes from
- [ETHICS.md](ETHICS.md): responsible use and limits
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
