# The Alert → DetectedIssue mapping

This is a read-only FHIR view over the same alert data the REST API already
exposes (`/api/v1/alerts`) — nothing here accepts a FHIR resource as input,
and nothing in this service is persisted as FHIR. HAPI FHIR is used as a
library (R4 structures, JSON parser, and `FhirInstanceValidator`), not as a
second (HAPI JPA) persistence layer.

## Why `DetectedIssue`, not `Flag`

Both resources can describe "something worth a clinician's or analyst's
attention." They were weighed against what an alert in this system actually
is: a statistical determination, with a severity, an evidentiary basis (an
algorithm, a score, a threshold), and a lifecycle that someone reviews and
concludes one way or another.

- **`Flag`** is deliberately simple: a `category`, a `code`, and a
  three-state `status` (`active` | `inactive` | `entered-in-error`). It fits
  a durable fact worth flagging (an allergy, a risk marker), not a
  time-bound signal that gets investigated and resolved.
- **`DetectedIssue`** has `severity`, `identified[x]`, `detail`, `evidence`,
  and — the deciding factor — a `status` value set rich enough to model a
  real review lifecycle (`registered`, `preliminary`, `final`, `cancelled`,
  and others), which is exactly what `AlertStatus`'s
  `OPEN → ACKNOWLEDGED → RESOLVED`/`DISMISSED` state machine already is.

`DetectedIssue` was chosen because the resource's own shape already matches
this system's domain model, not forced to fit it.

## Field-by-field mapping

| `DetectedIssue` field | Source | Notes |
|---|---|---|
| `id` | `Alert.id` | |
| `status` | `Alert.status`, via `AlertStatusFhirMapping` | See below — deliberately bijective |
| `severity` | `Alert.severity` | `LOW`/`MEDIUM`/`HIGH` → `low`/`moderate`/`high` |
| `code` | `Alert.algorithm` | This project's own code system (below), not a standard terminology |
| `identified[x]` (`dateTime`) | `Alert.createdAt` | When the algorithm raised it |
| `detail` | `algorithm`, `score`, `threshold`, `dataElementName`, `orgUnitName`, `periodStart` | Plain text, e.g. "EARS_C3 detected a score of 9.5 against a threshold of 3.0 for IDSR Malaria in Kpanga Kabonde (week of 2026-01-05)." |
| extension (`org-unit`) | `Alert`'s series' org unit | See "Why an extension, not `implicated`" below |

### Status: a deliberately bijective mapping

| `AlertStatus` | `DetectedIssue.status` |
|---|---|
| `OPEN` | `registered` |
| `ACKNOWLEDGED` | `preliminary` |
| `RESOLVED` | `final` |
| `DISMISSED` | `cancelled` |

Four distinct `AlertStatus` values map to four distinct codes from the same
R4 value set — not the two that might read more "naturally" at first glance
(`preliminary` for anything not yet finished, `final`/`cancelled` for
anything that is). Being bijective is what lets a FHIR `status` search
parameter round-trip back to exactly one `AlertStatus` and reuse the same
indexed, paginated database query the REST API already has
(`AlertRepository.search`). A one-to-many mapping would have forced either
an in-memory filter after fetching every alert, or a second query shape just
for FHIR search. See `DECISIONS.md`.

A search for any other syntactically valid `DetectedIssueStatus` code (for
example `amended` or `entered-in-error`) returns an empty `Bundle`, not an
error: that code is simply not one this mapping ever produces, so nothing
can ever match it.

### Code system: this project's own

R4 has no standard terminology for "which statistical aberration-detection
algorithm raised this". Rather than force-fit an unrelated standard
terminology (there isn't a close one), `code` uses a code system this
project mints for itself:

```
https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/fhir/CodeSystem/detection-algorithm
```

with the same stable algorithm identifiers (`EARS_C1`, `CUSUM`, ...) the REST
API already reports.

### Why an extension, not `implicated`

`DetectedIssue.implicated` is meant for references to resources such as
`Medication` or `Procedure` that are implicated in a clinical issue. This
project has no FHIR resource modelling an organisation unit (no `Location`,
no `Organization`) — a reference there would either be a logical reference
to a resource that does not exist, or a misuse of a resource type this
system does not actually model. A self-defined extension carries the same
information (`id`, `name`) honestly instead:

```
https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/fhir/StructureDefinition/org-unit
```

## Endpoints

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /fhir/DetectedIssue/{id}` | Any authenticated role | `404` is an `OperationOutcome`, not the REST API's `ProblemDetail` — see below |
| `GET /fhir/DetectedIssue?status=&identified=` | Any authenticated role | See "Search parameters" below |
| `GET /fhir/metadata` | None (public, like `/swagger-ui`) | A `CapabilityStatement` built fresh per request, listing only what the two endpoints above actually do |

Content negotiation: `application/fhir+json` and `application/json` are both
accepted and both produce real FHIR JSON via HAPI's own parser
(`FhirJsonHttpMessageConverter`), not Jackson reflecting over the Java
object — Jackson has no idea how a FHIR resource's polymorphic, choice-typed
fields are supposed to look on the wire.

### Search parameters

| Parameter | Type | Behaviour |
|---|---|---|
| `status` | token | One of `registered`, `preliminary`, `final`, `cancelled`. Any other value matches nothing (see above). |
| `identified` | date | **Known simplification:** matches the alerted week (`Alert.periodStart`), not the literal `identified` instant each resource reports. `periodStart` is the nearest date already indexed and queryable on `Alert`; adding separate date-range query support for `createdAt` was not worth a second query shape for this phase. Only exact-day matching is supported — no `ge`/`le`/`gt`/`lt` prefixes. |

### Why `OperationOutcome` on error, not `ProblemDetail`

A FHIR client expects a FHIR resource in every response, failures included.
`fhir.FhirExceptionHandler` is scoped to this package's own controllers
(`@RestControllerAdvice(basePackages = ...)`) so it — not the REST API's
`common.ApiExceptionHandler` or `detection.AlertExceptionHandler` — handles
everything thrown by `DetectedIssueController`, including the exact same
`AlertNotFoundException` the REST API also reuses. See `DECISIONS.md` for
why that package-scoping is what makes the two advices resolve
deterministically instead of by registration order.

## Validation

Every (`AlertStatus` × `Severity`) combination — 12 in total — is mapped and
validated against the base R4 profile with `FhirInstanceValidator` in
`FhirValidationTest`, and the full lifecycle of four real alerts (one per
status), driven through the real `AlertService` transitions against a real
PostgreSQL, is validated again in `FhirAlertServiceIntegrationTest`. Both
assert zero `ERROR`/`FATAL` validation messages — HAPI's own
`ValidationResult.isSuccessful()`. No project-specific `StructureDefinition`
is declared, so this is validation against the base profile plus this
project's own two open, undeclared extensions, which FHIR's open
extensibility model allows without error.

## What is not implemented

- No write operations (`create`/`update`/`delete`) on any FHIR resource.
- No FHIR resources other than `DetectedIssue`, `Bundle` (searchset) and
  `CapabilityStatement`.
- No date-range search prefixes (`ge`, `le`, `gt`, `lt`) on `identified`.
- No `_include`/`_revinclude`, chained searches, or `_sort`.
- No DHIS2 message posting on alert open (the plan's own stretch goal for
  this phase) — not built.

`GET /fhir/metadata`'s `CapabilityStatement` lists exactly the interactions
and search parameters above; nothing more.
