-- Synthetic load-test data — not derived from any real DHIS2 instance, no
-- patient or case-level data — matching this project's "synthetic and
-- public-demo data only" rule (see RESULTS.md's "Phase 7: load test").
-- Scaled well past the real DHIS2 demo (152 districts x 5 data elements =
-- 760 series) to put real pressure on the alert search query.
--
-- Usage: against a running `docker compose up` stack,
--   docker compose exec -T db psql -U outbreak -d outbreaksignal < loadtest/seed.sql
-- Not run in CI or by any application code; a one-off local fixture only.

-- 500 org units
INSERT INTO org_unit (dhis2_uid, name, level, parent_id)
SELECT 'LT' || lpad(gs::text, 8, '0'), 'Load Test District ' || gs, 3, NULL
FROM generate_series(1, 500) gs;

-- 5 series per org unit = 2500 series
INSERT INTO series (org_unit_id, dhis2_data_element_uid, name, frequency)
SELECT o.id, 'DE' || lpad(((o.id - 1) * 5 + d)::text, 9, '0'), 'Data Element ' || d, 'WEEKLY'
FROM org_unit o, generate_series(1, 5) d
WHERE o.dhis2_uid LIKE 'LT%';

-- One detection run per algorithm, to attach alerts to
INSERT INTO detection_run (algorithm, params_json, window_weeks, started_at, finished_at, series_evaluated, alerts_created)
SELECT alg, '{}', 104, now(), now(), 0, 0
FROM unnest(ARRAY['MOVING_AVERAGE_Z', 'EARS_C1', 'EARS_C2', 'EARS_C3', 'CUSUM']) alg;

-- 100 alerts per series across 2500 series = 250,000 alerts, spread across
-- algorithms, statuses and roughly two years of weeks.
INSERT INTO alert (series_id, period_start, algorithm, score, threshold, severity, status, detection_run_id, created_at, version)
SELECT
    s.id,
    date_trunc('week', now())::date - (n * 7),
    (ARRAY['MOVING_AVERAGE_Z', 'EARS_C1', 'EARS_C2', 'EARS_C3', 'CUSUM'])[1 + (n % 5)],
    (3.0 + random() * 10)::numeric(14,4),
    3.0,
    (ARRAY['LOW', 'MEDIUM', 'HIGH'])[1 + (n % 3)],
    (ARRAY['OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'])[1 + (n % 4)],
    (SELECT id FROM detection_run WHERE algorithm = (ARRAY['MOVING_AVERAGE_Z', 'EARS_C1', 'EARS_C2', 'EARS_C3', 'CUSUM'])[1 + (n % 5)] LIMIT 1),
    now() - (n * 7) * interval '1 day',
    0
FROM series s, generate_series(0, 99) n
WHERE s.dhis2_data_element_uid LIKE 'DE%'
ON CONFLICT DO NOTHING;

SELECT (SELECT count(*) FROM org_unit) AS org_units, (SELECT count(*) FROM series) AS series,
       (SELECT count(*) FROM alert) AS alerts;
