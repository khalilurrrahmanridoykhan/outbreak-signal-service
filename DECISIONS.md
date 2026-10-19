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

## 19. An alert is created with a Java find-then-save, not a native upsert

The plan's own wording for this ("unique constraint plus `INSERT … ON
CONFLICT DO NOTHING` semantics") suggested a native query, but
`DetectionService` instead checks `AlertRepository.findBySeriesIdAndPeriod...`
and only saves if nothing came back — the same pattern already used for
`Observation`, `Series` and `OrgUnit` (see decision 2's neighbours). The
required behaviour is identical either way (an existing alert, and any
human review of it once Phase 4 adds that, is never touched by a later
detection run), and matching the codebase's one established pattern beat
introducing a second, native-SQL one for just this table. The same
ShedLock-based "only one writer" reasoning already relied on for the sync's
own upserts applies here too.

## 20. Jackson 3 lives under `tools.jackson.*`, not `com.fasterxml.jackson.*`

Spring Boot 4.1 pulls Jackson 3 (`spring-boot-starter-jackson` →
`tools.jackson.core:jackson-databind`), which moved its core and databind
packages to the `tools.jackson` namespace; `jackson-annotations` is the one
module that kept its old `com.fasterxml.jackson.annotation` package. Reaching
for `com.fasterxml.jackson.databind.ObjectMapper` out of habit fails to
compile with no hint why beyond "package does not exist" — worth knowing
before it costs someone else the same lookup. `DetectionService` injects the
Boot-provided `tools.jackson.databind.ObjectMapper` bean rather than
constructing its own, so its settings (if the app ever configures any) stay
consistent with whatever serialises the REST API's own JSON.

## 21. Severity is a fixed ratio of score to threshold, the same for every algorithm

`Severity.fromScoreOverThreshold` uses one simple rule (at least double the
threshold is `HIGH`, one and a half times is `MEDIUM`, otherwise `LOW`)
applied identically across all five strategies, even though their
thresholds are each calibrated in different units underneath. This is a
placeholder good enough to populate the column now; revisit if a later
phase needs severity to carry a more specific meaning (for example, tied to
clinical case-fatality thresholds per disease) rather than purely how far
past its own trigger a statistic landed.

## 22. Detection runs as the second step of the sync's own schedule, not its own

`Dhis2SyncScheduler.scheduledSync()` calls `syncService.sync()` then
`detectionService.runDetection()` in the same `@Scheduled` method, under the
one ShedLock lock — matching the plan's "scheduled to run after each
successful sync" rather than giving detection an independent cron and a
second lock name. `Dhis2SyncService.sync()` never throws (decision 17), so
detection always runs once sync has at least attempted every data element.

## 23. The strategy classes are public, for the backtest's sake

Phase 3's hand-verified unit tests sit in the same package as the strategies
and so never needed public visibility. The backtest (`detection.backtest`,
a genuinely separate sub-package, matching the plan's own description of it
as "a backtest module") needs to construct the same five strategies with
their production defaults, which meant making the classes and their
constructors public. Nothing about the architecture rule in decision-adjacent
`PackageDependencyTest` changes — only `detection` package internals outside
the strategies' own visibility boundary, not the one-directional dependency
on `datasource`, which this does not touch.

## 24. The backtest reports two false-alarm numbers because CUSUM's statistic decays slowly

Measuring raw alarmed-weeks-outside-the-window as "false alarms" badly
overstates CUSUM's error rate: once its cumulative statistic crosses the
threshold during a real injected outbreak, it takes roughly 20-30 weeks of
calm data afterward to fall back below it, and every one of those weeks
counted as a separate false alarm under the naive metric (19.4 per 100
weeks). Collapsing a run of consecutive alarmed weeks into one "episode"
before counting brings CUSUM in line with the other four strategies (1.7 per
100, versus their 1.1-2.1) — confirming this was a measurement artifact of a
slow-resetting statistic, not CUSUM genuinely raising far more unrelated
false alarms. Both numbers are kept in `RESULTS.md` rather than only the
flattering one, with the explanation; see that file for the full numbers.

## 25. The manual `POST /api/v1/sync` trigger does not also run detection

Found while manually verifying this phase against the live DHIS2 demo: a
sync triggered by hand left `detection_run` and `alert` both empty, because
only the cron-scheduled path chains sync into detection
(`Dhis2SyncScheduler`); `Dhis2SyncController` calls `Dhis2SyncService.sync()`
alone. On reflection this matches the plan, not a bug in it: Phase 4 lists a
separate manual `POST /detection/run`, meaning the two are meant to be
independently triggerable actions, and only the *scheduled* run is specified
to chain them ("scheduled to run after each successful sync"). No production
code changed; what did change is that `Dhis2SyncSchedulerTest` now exists,
since nothing before this had actually exercised the scheduler's own wiring
— every other test covers `Dhis2SyncService` or `DetectionService` directly,
never the thing that chains them.

## 26. Full-context tests moved off `WebEnvironment.NONE`

`Dhis2SyncServiceIntegrationTest`, `DetectionServiceIntegrationTest` and
`AlertServiceIntegrationTest` all used `@SpringBootTest(webEnvironment =
WebEnvironment.NONE)`, since none of them make an HTTP call. Adding
`SecurityConfig`'s `SecurityFilterChain` bean broke all three: Spring Boot's
security auto-configuration does not register the `HttpSecurity` bean
`WebSecurityConfiguration` provides in a non-web application context, so the
bean method requiring it had nothing to autowire. None of the three needed
`NONE` specifically — the default `MOCK` web environment fixes it with no
change to what any of them exercise.

## 27. `SecurityConfig` needs `@EnableWebSecurity` explicitly

Spring Boot normally enables `@EnableWebSecurity`'s machinery itself once it
sees a `SecurityFilterChain` bean in a full application context, which is why
the bean worked without it there. The same implicit enabling did not reliably
reach `@WebMvcTest` slices in this Spring Boot/Security combination — the
slice's auto-configuration imported `OAuth2ResourceServerWebSecurityAutoConfiguration`
but never `WebSecurityConfiguration` itself, so the `HttpSecurity` bean was
still missing there too. Adding `@EnableWebSecurity` directly to
`SecurityConfig` makes it register unconditionally, in both places, rather
than depending on an auto-enabling behaviour that does not reach every
context type.

## 28. A `@ControllerAdvice`'s catch-all can shadow another advice bean's specific handler

`ExceptionHandlerExceptionResolver` picks the first `@ControllerAdvice` bean
(ordered by `@Order`, defaulting to `Ordered.LOWEST_PRECEDENCE` when
unannotated) that has *any* applicable handler — it does not scan every
advice bean in the application for the single most specific match. With
`AlertExceptionHandler`/`ObservationExceptionHandler` both left at the
default order, they tied with `ApiExceptionHandler`'s default, and whichever
bean happened to be checked first "won" for every exception, including ones
only the *other* bean had a specific handler for. In practice this turned
`AlertNotFoundException`, `IllegalAlertTransitionException` and
`SeriesNotFoundException` into generic 500s via `ApiExceptionHandler`'s
`Exception.class` catch-all, caught by `AlertControllerTest` and
`SeriesControllerTest` once they exercised real (not pre-built) 404/409
cases. Fixed by giving the domain-specific advice classes
`@Order(Ordered.HIGHEST_PRECEDENCE)` and the generic one
`@Order(Ordered.LOWEST_PRECEDENCE)` explicitly, rather than relying on two
defaults happening to break the tie the right way. The same catch-all was
also swallowing Spring Security's `AccessDeniedException` as a 500 instead of
403, for a related reason: a `@PreAuthorize` denial is thrown from inside the
controller's own call stack, so a `HandlerExceptionResolver`-based catch-all
intercepts it before `ExceptionTranslationFilter` (further out, at the filter
chain level) ever sees it. `ApiExceptionHandler` now maps
`AccessDeniedException` to 403 itself.

## 29. DTO mapping that touches a lazy association must happen inside the owning service's transaction

`AlertSummary.from(alert)` reads `alert.getSeries().getOrgUnit()`, both lazy
associations (`spring.jpa.open-in-view` is off, per decision 8's neighbours).
`AlertService.search()`/`AlertController.list()` used to return `Page<Alert>`
and map it to `Page<AlertSummary>` in the controller, after the loading
transaction had already closed — throwing `LazyInitializationException` on
every *real* alert. No test caught this: `AlertServiceIntegrationTest` only
asserted `.getStatus()` (an eager field, never a lazy one), and the
`@WebMvcTest` slices mock `AlertService` entirely, returning hand-built
`Alert`/`Series`/`OrgUnit` objects that are plain Java objects with no proxy
at all, so there was never anything to lazily fail to load. It surfaced only
while verifying this phase end to end against real Postgres data (see
`docs/keycloak.md`). Fixed by moving every DTO mapping into `AlertService`
and `SeriesService` themselves, each wrapped in `@Transactional(readOnly =
true)`, so the session is still open when the lazy association is actually
read; `AlertController`/`SeriesController` now only ever see already-built
DTOs. A new regression test
(`AlertServiceIntegrationTest.searchAndDetailResolveTheLazyOrgUnitAndSeriesNamesWithoutError`)
asserts the real `orgUnitName`/`dataElementName` values, not just that no
exception was thrown, so a future refactor that reopens this gap fails loudly.

## 30. An alert's `actor` is the JWT's `sub` claim, a stable id, not the username

`Principal.getName()` on the `JwtAuthenticationToken` Spring Security builds
returns the token's `sub` claim by default — in Keycloak's case, the user's
internal UUID, not `preferred_username`. Recording that UUID in `alert_event`
rather than switching to a more "readable" claim is deliberate: `sub` is the
one claim guaranteed stable for the life of the account, where a username
could be changed; an audit trail is exactly the place that stability matters
more than readability. A UI built on this API is where a username lookup
belongs, not the audit record itself.

## 31. The optional Keycloak profile needs two non-obvious fixes to actually issue a token

Verifying Phase 4 end to end (not just through mocked tests) against a real
Keycloak surfaced two problems neither obvious from the error Keycloak gives
nor from anything in this project's own code:

- Keycloak 26's declarative User Profile requires `firstName` and `lastName`
  on every user; without them the password grant fails with
  `"Account is not fully set up"` / `resolve_required_actions`, an error that
  names neither field. `keycloak/realm-export.json`'s three users set both.
- The app reaches Keycloak through Compose's internal network
  (`http://keycloak:8080/...`), never `localhost` — `localhost` inside the
  `app` container means the container itself. Pointing the app's
  `OUTBREAK_SECURITY_JWK_SET_URI` at `localhost:8081` (the host-mapped port,
  correct for a `curl` run from the host) made every call fail with a bare
  401 regardless of the token, which looks like a validation failure but was
  really a DNS one. `docker-compose.yml`'s `app` service now sets the
  internal URI explicitly; see `docs/keycloak.md` for both.

## 32. CSRF protection is deliberately disabled, and CodeQL's flag for that is a false positive here

`SecurityConfig` disables CSRF (`java/spring-disabled-csrf-protection`,
CodeQL's generic rule, flagged this as a high-severity finding on the PR).
CSRF attacks work by riding an *ambient* credential a browser attaches
automatically to a cross-site request — a cookie. This API has no
cookie-based session at all (`SessionCreationPolicy.STATELESS`); every
request carries its own bearer token in an `Authorization` header, which a
browser never attaches to a cross-origin request on its own. There is
nothing for CSRF to exploit, which is exactly the case Spring Security's own
reference documentation names as the right time to disable it. Suppressed
with an inline `codeql[java/spring-disabled-csrf-protection]` comment and the
corresponding alert dismissed as a false positive, both with this same
reasoning, rather than either leaving the PR blocked or "fixing" it by adding
CSRF protection a stateless bearer-token API gets no benefit from.