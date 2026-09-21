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
