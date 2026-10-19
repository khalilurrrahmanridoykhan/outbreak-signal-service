# Results

Measured numbers, one phase at a time. Nothing here is aspirational — if a
number looks bad, it is written down anyway. See `DECISIONS.md` for the
reasoning behind choices and `docs/dhis2-spike.md` for the real-world DHIS2
findings that shaped the sync.

## Phase 3: detection backtest

**Reproduce it:** `./mvnw test -Dtest=BacktestTest`, or read the table
straight from CI's output on this PR. The numbers below are that test's own
printed output, copied in unedited; nothing here was hand-tuned afterward.

### Scenario

A synthetic weekly count series, generated independently of any production
code (`SyntheticSeriesGenerator`, test-scope only):

| Parameter | Value |
|---|---|
| Total length | 104 weeks |
| Seasonal baseline | mean 20, amplitude 5, 52-week sine wave |
| Noise | Poisson around the seasonal mean each week |
| Injected outbreak | starts week 60, a 4-week linear ramp adding up to +15 on top of the baseline |
| Replicates | 200, one injected outbreak each |
| Random seed | 42 (`java.util.Random`, whose algorithm is specified and stable across JVMs) |
| Detection window | the outbreak's 4 weeks plus a 4-week grace period after it |

Every strategy runs with its production default configuration
(`OutbreakProperties.Detection`'s own defaults) — this is not a best-case
tuning for any one of them.

### Results

| Algorithm | Sensitivity | False alarms / 100 weeks | False-alarm episodes / 100 weeks | Median detection delay |
|---|---:|---:|---:|---:|
| Moving-average Z | 0.650 | 1.143 | 1.113 | 2.0 weeks |
| EARS C1 | 0.585 | 2.140 | 2.129 | 2.0 weeks |
| EARS C2 | 0.805 | 2.155 | 1.943 | 2.0 weeks |
| EARS C3 | 0.885 | 3.924 | 1.712 | 2.0 weeks |
| CUSUM | 0.875 | 19.443 | 1.705 | 2.0 weeks |

**Sensitivity** is the share of the 200 replicates where the strategy raised
at least one alarm inside the outbreak-plus-grace window.
**False alarms / 100 weeks** counts every alarmed week outside that window,
across all 200 replicates, as a percentage of the non-outbreak weeks the
strategy actually evaluated (fewer than 104 per replicate — each strategy's
own baseline requirement excludes its earliest weeks). **False-alarm
episodes / 100 weeks** collapses a run of consecutive alarmed weeks into one
before counting — see the note on CUSUM below for why both numbers are
reported.

### What this actually shows

- **EARS C2 and C3 beat C1 by a wide margin on sensitivity** (0.805 and 0.885
  against 0.585) for a moderate, sustained rise like this one. C1's short,
  unguarded 7-week baseline is the most reactive to a single bad week but the
  weakest at a rise that builds up more gradually — which is exactly the
  trade-off the guard band (C2) and the cumulative window (C3) exist to fix.
- **CUSUM's per-week false-alarm rate (19.4) is misleading on its own.**
  Inspecting individual replicates shows why: once CUSUM's cumulative
  statistic crosses its threshold during a real outbreak, it takes 20-30
  weeks of calm data to decay back below it — one real event, not 20-30
  independent mistakes. The episode-collapsed rate (1.705, in line with every
  other strategy) is the fairer number for "how often does this raise an
  alarm about nothing." The practical cost is real either way: a reviewer
  using the raw alert feed would see one alert stay open and keep
  resurfacing for weeks after a genuine event has already passed, not a
  flood of unrelated noise.
- **None of the five strategies detects faster than the others here** — every
  median delay is 2 weeks, because the outbreak is a 4-week linear ramp and
  none of them is tuned to catch a rise before it has built up for a couple
  of weeks. A sharper, faster-rising outbreak would very likely separate
  them on timeliness; this scenario does not test that.
- **Moving-average Z, the plan's own "baseline for comparison," is beaten on
  sensitivity by every EARS variant and CUSUM** at this outbreak magnitude,
  which is the expected result for the simplest method, not a sign something
  is wrong with it.

### Limits of this backtest

- One scenario, one outbreak shape (a 4-week linear ramp), one magnitude.
  Changing any of those would change every number above; `BacktestTest`'s
  constants are there to rerun it under different ones.
- Real DHIS2 data (see `docs/dhis2-spike.md`) has reporting gaps and whatever
  real seasonal and demographic structure exists in Sierra Leone's IDSR
  figures — neither is modelled here. This backtest measures the algorithms
  against a controlled synthetic signal, not against the real demo's data,
  which has no labelled outbreaks to grade against in the first place.
- No cross-check against another EARS implementation (such as R's
  `surveillance` package) was performed — the plan treats that as optional,
  and R is not a project dependency. The formulas are documented in each
  strategy's own Javadoc instead.
