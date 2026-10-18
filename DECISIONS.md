# Decisions

Short records of choices that are not obvious from the code. Newest last.

## 1. Spring Boot 4.1, Java 25, Maven

Java 25 is the current long-term-support release and Spring Boot 4.1.x is the
current stable line, so the project starts on the supported baseline instead of
migrating later. Maven, with the wrapper committed, means contributors need only
a JDK.

Note: start.spring.io labels this release `4.1.1.RELEASE`, but the artifact on
Maven Central is `4.1.1`. The POM uses `4.1.1`.

## 2. A week is stored as its Monday (`week_start DATE`)

Weekly surveillance periods are ISO weeks. Storing the Monday that starts the
week as a `DATE` sorts naturally, avoids the year-boundary and 53-week traps of
a `(year, week)` pair, and lets the database itself enforce the rule with a
`CHECK` on `ISODOW`. Period strings from the source system (for example
`2026W12`) are converted at the edge.

## 3. Flyway owns the schema; Hibernate only validates

`spring.jpa.hibernate.ddl-auto=validate`. Every schema change is a reviewed SQL
migration, and a mismatch between entities and schema fails at startup instead of
silently drifting. Merged migrations are never edited.

## 4. Constraints live in the database, not only in Java

Uniqueness of (series, week), the Monday rule and non-negative values are
`UNIQUE` and `CHECK` constraints. Application code can be bypassed, a constraint
cannot. Tests assert each one against a real database.

## 5. Tests use real PostgreSQL through Testcontainers

Not H2. The schema uses PostgreSQL features (identity columns, `ISODOW`,
`NUMERIC`), and outbox and concurrency behaviour planned for later phases depends
on real PostgreSQL semantics. The image is pinned to `postgres:17-alpine`, the
same as Compose.

## 6. Plain classes for entities, no Lombok

Entities are ordinary classes with explicit accessors. It is a little more
typing and a lot less magic, and it keeps the persistence layer readable.
Immutable data carriers elsewhere use records.

## 7. Problem details (RFC 9457) with a generic 500

All errors are `application/problem+json`. Unexpected exceptions return a fixed
message and are logged server-side, so stack traces, SQL and internal messages
never reach the client.

## 8. Spring Security arrives with the API (Phase 4)

The first phases have no business endpoints to protect, and a permissive
placeholder security configuration would be code written only to be removed. The
resource-server setup lands together with the endpoints it guards.

## 9. Mail sandbox arrives with notifications (Phase 5)

Compose runs the application and PostgreSQL. A mail catcher is added in the phase
that sends email.

## 10. Commit identity guard

Commits are authored only by the maintainer. A `commit-msg` hook rejects
`Co-Authored-By` trailers and assistant attribution locally, and the
`commit-lint` workflow repeats the check on pull requests so a skipped hook is
still caught.

## 11. GitHub Actions are pinned to commit SHAs

Third-party actions are referenced by full commit SHA with the version in a
comment, and Dependabot keeps them current. A moved tag cannot change what runs.

## 12. resilience4j and ShedLock are used as plain libraries, not autoconfigured

`resilience4j-spring-boot3` targets Boot 3's autoconfigure package layout, which
Boot 4 reorganised (see decision 1); rather than find out at some later phase
whether its autoconfiguration still resolves, `DhisResilienceConfig` builds the
`Retry` and `CircuitBreaker` beans by hand from the core `resilience4j-retry`
and `resilience4j-circuitbreaker` modules. ShedLock was never a concern here —
`shedlock-spring` is a plain `LockProvider` bean with no Boot-version-specific
autoconfiguration at all.

## 13. DHIS2 analytics columns are read by header name, not by position

`AnalyticsResponse` looks up `dx`/`ou`/`pe`/`value` by the `headers` array DHIS2
sends rather than assuming `[dx, ou, pe, value]` order. The spike's own queries
always came back in that order, but nothing in the DHIS2 API contract promises
it, and the cost of reading headers instead of hardcoding positions is one
small lookup method.

## 14. A decoding failure is not retried

`DhisWebClient` retries `DhisServerException` (5xx, connection failure) but not
a response-decoding failure. A malformed body from the same endpoint would very
likely still be malformed on retry; that failure mode is closer to a 4xx (a
request/response mismatch worth surfacing immediately) than to a transient
network blip.

## 15. A `@Component`'s default bean name can collide with a `@Bean` method name

`DhisWebClient` (the `@Component` adapter) and a `@Bean WebClient dhisWebClient(...)`
method in `DhisWebClientConfig` both wanted the bean name `dhisWebClient` —
Spring derives a component's default name from its simple class name
(`DhisWebClient` → `dhisWebClient`), and that collided with the `WebClient`
factory method of the same name. It only surfaced as a context-load failure in
`OutbreakSignalServiceApplicationTests`, not at compile time. Renamed the
factory method to `dhisHttpClient`; the lesson generalises, so a `@Bean` method
in this codebase is never named after an existing `@Component` class with the
same simple name.

## 16. Synced org units are stored flat, with no parent link, for now

The `org_unit` table's `parent_id` is a self-referential foreign key, but
Phase 2 syncs only one configured hierarchy level (districts). Persisting a
district's real DHIS2 parent (a region, one level up) would violate that
foreign key, since the region is never synced as a row of its own. Rather than
also sync every ancestor level just to make the column valid, `parent_id` is
left `null` for every row Phase 2 creates; `DhisOrgUnit.parentUid()` is read
from the API but intentionally not persisted yet. Revisit if a later phase
needs to aggregate upward through the hierarchy.

## 17. One data element's sync failure does not stop the others

`Dhis2SyncService.sync()` catches `DhisClientException` per data element
rather than letting it propagate and abort the whole run. The sync is
idempotent (decision in the plan itself), so the next scheduled run simply
retries whatever failed; getting four diseases' worth of data because the
fifth's request failed is strictly better than getting none of the five over
a transient problem with one.

## 18. The DHIS2 demo's "fixed high-water mark" finding from the spike was wrong

`docs/dhis2-spike.md` originally found (2026-09-21) that the demo's weekly
data stopped advancing at a fixed week and would never move. Returning to
build the sync a month later (2026-10-18), the real cutoff had moved forward
by about eight months of ISO weeks — far more than the one month of real time
that had passed — while the older data stayed in place. The spike document
itself was corrected in place with a dated addendum rather than quietly
edited, since the wrong version had already shaped real design decisions
(notably the live test's assertions) worth knowing the history of. The
practical lesson carries over unchanged either way: a sync must treat an
empty or short recent window as normal, and a test must never assert
freshness against "the newest week," regardless of which explanation (frozen
seed, or bursty reseeding) turns out to be correct on any given day.
