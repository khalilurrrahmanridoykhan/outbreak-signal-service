// k6 load test for GET /api/v1/alerts and GET /fhir/DetectedIssue — see
// RESULTS.md's "Phase 7: load test" for the measured numbers and what they
// do and do not show.
//
// Usage: seed loadtest/seed.sql first, mint a token (docs/keycloak.md),
// then:
//   TOKEN=$TOKEN k6 run loadtest/alerts-search.js
//
// outbreak.rate-limit.* will throttle k6's own concurrency long before this
// script does — raise OUTBREAK_RATE_LIMIT_REQUESTS_PER_MINUTE /
// OUTBREAK_RATE_LIMIT_BURST_CAPACITY for a load-testing run specifically;
// the defaults are sized for real traffic, not a deliberate stress test.
import http from 'k6/http';
import { check } from 'k6';

const TOKEN = __ENV.TOKEN;
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = __ENV.VUS ? Number(__ENV.VUS) : 10;
const DURATION = __ENV.DURATION || '15s';

export const options = {
  scenarios: {
    alerts_filtered: {
      executor: 'constant-vus',
      exec: 'alertsFiltered',
      vus: VUS,
      duration: DURATION,
    },
    alerts_unfiltered: {
      executor: 'constant-vus',
      exec: 'alertsUnfiltered',
      vus: VUS,
      duration: DURATION,
      startTime: DURATION,
    },
    fhir_search: {
      executor: 'constant-vus',
      exec: 'fhirSearch',
      vus: VUS,
      duration: DURATION,
      startTime: `${2 * parseInt(DURATION, 10)}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
  },
};

const headers = { headers: { Authorization: `Bearer ${TOKEN}` } };

// Matches the seed's 500 org units (loadtest/seed.sql) — the realistic
// query pattern: an analyst reviewing alerts for their own district.
export function alertsFiltered() {
  const orgUnitId = 1 + Math.floor(Math.random() * 500);
  const res = http.get(`${BASE}/api/v1/alerts?orgUnitId=${orgUnitId}&page=0&size=20`, headers);
  check(res, { 'alerts filtered 200': (r) => r.status === 200 });
}

// The pessimistic case: no org unit, so every series has to be considered.
export function alertsUnfiltered() {
  const res = http.get(`${BASE}/api/v1/alerts?status=OPEN&page=0&size=20`, headers);
  check(res, { 'alerts unfiltered 200': (r) => r.status === 200 });
}

export function fhirSearch() {
  const res = http.get(`${BASE}/fhir/DetectedIssue?status=registered`, headers);
  check(res, { 'fhir search 200': (r) => r.status === 200 });
}
