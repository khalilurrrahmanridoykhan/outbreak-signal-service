# Runbook

What to do when the scheduled sync fails, the outbox backs up, or a webhook
dead-letters. Every metric and log field named here is real — see
`CorrelationIdFilter`, `Dhis2SyncService`, `NotificationMetrics`, and
`WebhookDispatchScheduler` for where each one is produced.

## Checking what's running

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/health
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/prometheus
```

`/actuator/prometheus` needs a token like every other endpoint except
`/actuator/health` and `/fhir/metadata` — see `SecurityConfig`. The metrics
below are this project's own; everything else on that page (JVM, HTTP,
Hikari, ...) is Spring Boot's and Micrometer's standard set.

| Metric | What it means |
|---|---|
| `outbreak_sync_duration_seconds{outcome=...}` | How long each scheduled DHIS2 sync took, and whether every data element succeeded (`success`), at least one failed (`partial-failure`), or the run itself threw (`failure`) |
| `outbreak_sync_failures_total{dataElement=...}` | Sync failures, by DHIS2 data element UID |
| `outbreak_alerts_created_total{algorithm=...}` | New alerts, by detection algorithm |
| `outbreak_outbox_lag_seconds` | Age of the oldest still-`PENDING` outbox event; `0` when nothing is pending |
| `outbreak_webhook_deliveries_pending` | Webhook deliveries waiting to be sent or retried |
| `outbreak_webhook_dispatch_failures_total{reason=...}` | Failed delivery attempts, by reason (`http-<status>` or `connection-error`) |

Container logs are structured JSON (ECS format) with a `correlationId`
field — one value per HTTP request (`CorrelationIdFilter`, also returned as
the `X-Correlation-Id` response header) or per scheduled sync run
(`Dhis2SyncScheduler`). Grep or filter by it to see everything one request
or one sync produced, across every layer:

```bash
docker compose logs app | grep '"correlationId":"<the-id>"'
```

Locally (`./mvnw` directly, not through `docker compose`), logging stays
plain console text — ECS JSON is only active under the `docker` Spring
profile (`application-docker.yml`), since a wall of one-line JSON is a
straight regression for reading test output and the Hibernate SQL this
project's own development workflow relies on.

## The scheduled sync fails

Symptoms: `outbreak_sync_duration_seconds{outcome="failure"}` increasing,
or `outbreak_sync_failures_total` for one data element climbing every run.

1. Check the logs for that sync's `correlationId` — `Dhis2SyncScheduler`
   logs "Starting scheduled DHIS2 sync" first, so the id is on that line.
2. A `DhisRequestException` (4xx) means the request itself is wrong —
   `outbreak.dhis2.*` credentials, org-unit level, or a data element UID
   that no longer exists on that instance. This will not fix itself on
   retry; it needs a configuration change.
3. A `DhisServerException` (5xx, connection failure, or the circuit breaker
   open) is DHIS2's own instance struggling — `Dhis2SyncService` already
   isolates one data element's failure from the others (DECISIONS.md #17)
   and retries with backoff (`DhisResilienceConfig`) before giving up for
   that run; the next scheduled run tries again automatically. Only step in
   if failures persist across several scheduled runs.
4. To force a retry without waiting for the next scheduled run:
   `POST /api/v1/sync` (`ADMIN`).
5. The sync is idempotent (DECISIONS.md #19) — re-running it after a
   partial failure is always safe, never duplicates data.

## The outbox backs up

Symptoms: `outbreak_outbox_lag_seconds` climbing instead of staying near
zero.

1. Check whether `OutboxFanoutScheduler`'s ShedLock
   (`shedlock` table, `name = 'outbox-fanout'`) is held by an instance that
   is no longer running — `lock_until` in the past but no running instance
   holds it is fine (the next poll just re-acquires it); `lock_until` far
   in the future with no corresponding live instance means a stale lock
   row from a crashed instance. Deleting that one row lets a live instance
   acquire it immediately, instead of waiting out `lockAtMostFor` (5
   minutes).
2. If the lock looks healthy, check for an exception in the fan-out
   poller's own logs (`OutboxFanoutScheduler`/`OutboxFanoutService`) — a
   failure there rolls back the whole batch's claim (so nothing is lost;
   see DECISIONS.md #33), but means that batch keeps retrying and failing
   every poll until whatever is wrong is fixed (commonly: the database
   connection pool exhausted, or a bug in a specific `WebhookSubscription`'s
   data causing fan-out itself to fail for every event, not just delivery).
3. `outbreak.notification.outbox.poll-interval-ms` / `.batch-size` can be
   tuned if the backlog is simply larger than the default pace can clear;
   this is rarely the real cause.

## A webhook dead-letters

Symptoms: `outbreak_webhook_dispatch_failures_total` for one endpoint
climbing, or an alert from `outbreak_webhook_deliveries_pending` staying
elevated.

1. `GET /api/v1/webhook-deliveries?status=DEAD_LETTERED` (`ANALYST` or
   `ADMIN`) lists every dead-lettered delivery, each with `lastError` and
   `attempts`.
2. `last_error` of the form `HTTP 4xx` means the subscriber's own endpoint
   is rejecting the request — check their side, not this service's;
   retrying without a fix there will dead-letter again identically.
3. `HTTP 5xx` or `connection-error` means the subscriber's endpoint was
   unreachable or erroring at delivery time; it may be transient. There is
   currently no "redeliver this one" endpoint — the subscriber needs to
   re-request the data through `GET /fhir/DetectedIssue` or
   `GET /api/v1/alerts` instead, since this service does not replay a
   specific dead-lettered event on demand.
4. Delivery is **at-least-once**, not exactly-once (DECISIONS.md #33,
   README): a receiver that does not deduplicate on `X-Event-Id` can
   process the same alert event twice after a retry. This is a receiver
   responsibility, not something this service can prevent from its side.
5. `outbreak.notification.webhook.max-attempts` / `.initial-backoff-ms` /
   `.backoff-multiplier` control how many attempts are made and how fast
   they back off before dead-lettering; the defaults (5 attempts, 10s
   initial, x3 multiplier) total a little over 7 minutes from first attempt
   to dead-letter.

## Restarting without losing in-flight work

`server.shutdown: graceful` and `spring.task.scheduling.shutdown.await-termination`
are both on: a `docker compose stop`/`SIGTERM` lets in-flight HTTP requests
finish and lets a `@Scheduled` method already running (a sync, an outbox
poll, a webhook dispatch batch) finish its current batch before the JVM
exits, up to 30 seconds (`spring.lifecycle.timeout-per-shutdown-phase`).
Nothing async is silently dropped mid-batch under a normal stop; only a
hard kill (`docker kill`, `SIGKILL`, a crashed host) can leave a
`webhook_delivery` row stuck `SENDING` — which is exactly what the
stale-`SENDING` reclaim window (`outbreak.notification.webhook.stale-sending-timeout-ms`)
exists for; see DECISIONS.md.

## The alert search endpoints are slow

See `RESULTS.md`'s "Phase 7: load test" section for the measured numbers
and a known, honest limitation: `GET /alerts`/`GET /fhir/DetectedIssue`
without an `orgUnitId` (or, for FHIR, `identified`) filter at a very large
alert count can be noticeably slower than the same query filtered by org
unit — a query-planner limitation in how the optional-filter JPQL pattern
interacts with the join to `series`, not a missing index (two were added in
`V6__alert_search_indexes.sql`; RESULTS.md explains why they do not fully
resolve this specific case). The realistic query pattern — reviewing one
district's alerts — is unaffected.
