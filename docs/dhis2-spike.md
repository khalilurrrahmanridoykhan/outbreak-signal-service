# DHIS2 spike

Phase 2 of the plan says to check what the public demo instance actually offers
before writing the client. This is that check. Every request below was a
read-only `GET` with the demo's public `admin`/`district` credentials; nothing
was created, updated or deleted.

**Date of this spike:** 2026-10-18.

## 1. Which instance, and how to reach it

`play.dhis2.org` is a stable alias maintained by the DHIS2 project. A path
under it (`/dev`, `/40`, a specific version) returns an HTTP **302** to the
instance that currently serves that line, hosted on a different domain,
`play.im.dhis2.org`:

```
GET https://play.dhis2.org/40/api/system/info
  -> 302 Location: https://play.im.dhis2.org/stable-2-40-12/api/system/info
```

At the time of this spike, `/40` resolved to `play.im.dhis2.org/stable-2-40-12`,
running **DHIS2 2.40.12**, named "DHIS 2 Demo - Sierra Leone". This matters for
the client:

- **The redirect crosses hosts.** A client that follows redirects and resends
  `Authorization` across hosts is a security smell in general; `WebClient`
  (via Reactor Netty) does not resend sensitive headers across origins by
  default, and curl drops Basic Auth on a cross-host redirect unless told not
  to. A GET through the redirect without re-sending credentials lands on the
  plain login page, not JSON.
- **The resolved host is a moving target.** `stable-2-40-12` will change on the
  next patch release; `play.dhis2.org/40` will not. Phase 2's `DhisClient`
  should be configured with **the resolved host as `outbreak.dhis2.base-url`**
  (an environment variable, per the plan), not the alias, and that value is
  expected to need updating occasionally. Resolving the alias automatically
  at startup was considered and set aside for later — it adds a dependency on
  the redirect behaviour staying this shape, for a problem solved just as
  well by a documented environment variable.
- The demo's login page states the instance **"is reset every night."** The
  client must never depend on anything written to this instance surviving,
  and the spike did not write anything.

## 2. A real weekly surveillance dataset exists

`GET /api/dataSets.json?fields=id,name,periodType&paging=false` lists an
**IDSR Weekly** dataset (`id: Nyh6laLdBEJ`, `periodType: Weekly`) — IDSR is
Integrated Disease Surveillance and Response, exactly the kind of source this
project is built for. (A `WeeklyWednesday` variant also exists and was not
pursued; ISO weeks starting Monday are the simpler match for this project's
schema.)

Its five data elements are what the plan hoped for:

| id | name | value type |
|---|---|---|
| `UsSUX0cpKsH` | IDSR Cholera | `INTEGER_ZERO_OR_POSITIVE` |
| `vq2qO3eTrNi` | IDSR Malaria | `INTEGER_ZERO_OR_POSITIVE` |
| `YazgqXbizv1` | IDSR Measles | `INTEGER_ZERO_OR_POSITIVE` |
| `HS9zqaBdOQ4` | IDSR Plague | `INTEGER_ZERO_OR_POSITIVE` |
| `noIzB569hTM` | IDSR Yellow fever | `INTEGER_ZERO_OR_POSITIVE` |

All five are `AGGREGATE` domain type, non-negative integers — a direct match
for the `observation.value >= 0` constraint already in `V1__baseline.sql`.

The dataset is assigned to **1,166 level-4 facilities** (plus the root), which
is too granular for a first client. Querying `analytics` with `ou:LEVEL-3`
aggregates these to Sierra Leone's **152 districts**; **99 of them** have at
least one real value for malaria in the data's active window (§3). This
project's `org_unit` table should be seeded at **district (level 3)**, not
facility level, matching the plan's example of "weekly dengue cases in one
district."

## 3. The analytics endpoint, and its real shape

```
GET /api/analytics.json
    ?dimension=dx:vq2qO3eTrNi
    &dimension=ou:LEVEL-3
    &dimension=pe:2025W46;2025W47;2026W1;2026W9
```

returns rows shaped exactly like the plan's data model expects:

```json
{"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}],
 "rows": [["vq2qO3eTrNi", "QwMiPiME3bA", "2026W24", "9"], ...]}
```

`pe` values are ISO week strings with **no zero-padding on the week number**
(`2026W9`, not `2026W09`). The period-parsing utility must handle both one and
two-digit week numbers. Checked directly: `date.fromisocalendar(2026, 9, 1)`
is `2026-02-23`, a Monday — ISO weeks do align to the Monday `week_start` rule
already enforced by the database.

Two traps found by trying the obvious things first:

- **Relative period keywords stop at `LAST_52_WEEKS`.** `LAST_104_WEEKS` is not
  a real DHIS2 relative period and is silently ignored, which then trips
  `E7104` ("at least one period... must be specified") because no period
  dimension survived. For a 104-week window (the plan's default), **the client
  must generate the explicit list of ISO week strings itself** and pass them
  all in one `pe:` dimension — tested with all 104 explicit weeks in one
  request; it returned in 1.4 seconds with no pagination needed.
- **`startDate`/`endDate` without a `pe` dimension collapses the whole range
  into one aggregate total per org unit**, dropping the weekly breakdown
  entirely (the `pe` column disappears from the response). `pe` must always be
  passed explicitly; `startDate`/`endDate` are for a different use case than
  this project's.
- (Tooling note, not DHIS2's fault: curl treats `[` and `]` in a URL as its own
  range-globbing syntax, so any query built with DHIS2's `field[nested]`
  syntax needs `curl -g` or `--globoff`.)

## 4. The data has a fixed high-water mark — read this before Phase 2 design

This is the most important finding. Querying all 104 weeks up to the current
server date (`2026-10-18`, ISO week 42) returns data for only **28 of those
104 weeks**, and the most recent one with any value is **`2026W9`** — about 33
weeks behind today, on every one of the five diseases, consistently. Data
starts around `2025W1`. Re-running the same query later in this session
produced the same cutoff.

In other words: **this demo's weekly values are a fixed seed, not a live feed
that advances with the calendar.** A sync job that asks "give me data for the
last N weeks relative to right now" will, on this instance, keep finding
nothing for every week after `2026W9`, indefinitely, through no fault of the
sync logic.

This directly shapes Phase 2:

- **Finding nothing for the newest weeks is an expected, normal outcome on
  this instance**, not a failure to retry or alert on. The sync's idempotent
  upsert already handles "nothing new" correctly by construction (it simply
  upserts zero rows); what this adds is that **tests and the opt-in live
  check must assert "the known historical window is present," not "a recent
  week has data,"** since the latter would fail here forever.
- Coverage within the active window is **not complete**: about 85–86% of
  (district × week) cells have a value for malaria, so missing weeks are
  real and expected, not a sign of a broken query. This matches the plan's
  already-written policy of skipping missing weeks and recording a
  data-quality note rather than zero-filling them.
- The three thresholds that matter for the test fixtures built on this data:
  active window roughly `2025W1`–`2026W9`, about 99–100 districts with any
  value, about 85% cell completeness.

## 5. What this changes in the plan

- **The spike's own "no suitable weekly series" fallback does not trigger.**
  IDSR Weekly is real, weekly, and a direct match; Phase 2 is built against
  it, not against invented synthetic shapes alone.
- **`outbreak.dhis2.base-url` should default to nothing** (fail closed) and be
  set in the environment to the currently-resolved host documented in §1,
  with a comment pointing at this file for why it occasionally needs updating.
- **Seed `org_unit` at level 3** (district), using the IDSR Weekly dataset's
  own analytics output, not the facility list.
- **The period utility generates explicit ISO week strings for the whole sync
  window** rather than relying on a relative keyword, and must format week
  numbers without zero-padding to match DHIS2's own output.
- **The opt-in `live` integration test** (excluded from CI, per the plan)
  asserts against the known historical window above, not against "this week."
- WireMock fixtures for the main test suite are recorded from real response
  shapes seen above (including a captured 104-week response), so the suite
  stays representative without depending on the demo being reachable or
  reset-stable.

## 6. Still open for the client build itself (not this spike)

- Whether to resolve `play.dhis2.org`'s redirect automatically at startup, or
  keep requiring the resolved host as configuration (leaning configuration,
  per §1).
- Whether DHIS2 Personal Access Tokens are a better fit than Basic Auth for a
  long-running sync job; not required to answer this spike's question, so
  left for the client-design step of Phase 2.
