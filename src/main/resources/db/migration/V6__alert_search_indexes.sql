-- AlertRepository.search's status-only and algorithm-only filters (the
-- plan's own "add the indexes the query plans actually need" — see
-- RESULTS.md and DECISIONS.md for the measured before/after). Without
-- these, neither filter can be satisfied without visiting every series via
-- ix_alert_series_status / uq_alert_series_period_algorithm (both lead with
-- series_id, which is of no help when the request has no series to start
-- from), forcing a nested loop over the whole series table.
CREATE INDEX ix_alert_status_period ON alert (status, period_start);
CREATE INDEX ix_alert_algorithm_period ON alert (algorithm, period_start);
