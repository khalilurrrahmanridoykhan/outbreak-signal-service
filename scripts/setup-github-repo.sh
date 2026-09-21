#!/usr/bin/env bash
# Applies the repository settings described in docs/repo-setup.md.
# Idempotent: safe to re-run. Needs the GitHub CLI (`gh`), logged in as a user
# with admin rights on the repository.
#
#   scripts/setup-github-repo.sh            # apply everything
#   REPO=owner/name scripts/setup-github-repo.sh
set -euo pipefail

REPO="${REPO:-khalilurrrahmanridoykhan/outbreak-signal-service}"
DESCRIPTION="Spring Boot service that pulls weekly surveillance data from DHIS2, flags statistical aberrations (EARS, CUSUM), and publishes alerts as REST, signed webhooks and FHIR DetectedIssue. Signals for review, not confirmed outbreaks."
TOPICS=(
  spring-boot java dhis2 fhir hapi-fhir public-health disease-surveillance
  outbreak-detection epidemiology early-warning health-informatics digital-health
  rest-api postgresql flyway testcontainers oauth2 openapi webhooks docker
)

echo "==> Repository metadata and features"
topic_args=()
for t in "${TOPICS[@]}"; do topic_args+=(--add-topic "$t"); done
gh repo edit "$REPO" \
  --description "$DESCRIPTION" \
  --enable-issues \
  --enable-discussions \
  --enable-wiki=false \
  --enable-projects=false \
  --enable-merge-commit \
  --enable-squash-merge=false \
  --enable-rebase-merge=false \
  --delete-branch-on-merge \
  "${topic_args[@]}"

echo "==> Security features"
gh api -X PUT "repos/$REPO/vulnerability-alerts" --silent
gh api -X PUT "repos/$REPO/automated-security-fixes" --silent
gh api -X PUT "repos/$REPO/private-vulnerability-reporting" --silent
gh api -X PATCH "repos/$REPO" --silent --input - <<'JSON'
{
  "security_and_analysis": {
    "secret_scanning": { "status": "enabled" },
    "secret_scanning_push_protection": { "status": "enabled" }
  }
}
JSON

echo "==> Labels"
label() { gh label create "$1" --color "$2" --description "$3" --force --repo "$REPO" >/dev/null; }
label bug               d73a4a "Something is not working"
label enhancement       a2eeef "New feature or request"
label documentation     0075ca "Improvements or additions to documentation"
label "good first issue" 7057ff "Good for newcomers"
label "help wanted"     008672 "Extra attention is needed"
label question          d876e3 "Further information is requested"
label dependencies      0366d6 "Dependency updates"
label security          b60205 "Security-related"
label breaking-change   e11d21 "Changes that break compatibility"
label duplicate         cfd3d7 "This already exists"
label wontfix           ffffff "This will not be worked on"
label area:datasource   c5def5 "DHIS2 client and sync"
label area:detection    c5def5 "Detection algorithms and backtest"
label area:alerts       c5def5 "Alerts API and lifecycle"
label area:notifications c5def5 "Webhooks, email, outbox"
label area:fhir         c5def5 "FHIR output"
label area:ops          c5def5 "CI, packaging, observability"

echo "==> Milestones"
existing="$(gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title')"
milestone() {
  if grep -Fxq "$1" <<<"$existing"; then return; fi
  gh api -X POST "repos/$REPO/milestones" -f title="$1" -f description="$2" --silent
}
milestone "Phase 1 - Scaffold and repository standards" "Spring Boot scaffold, PostgreSQL schema, CI, public repository setup"
milestone "Phase 2 - DHIS2 client and sync"             "DHIS2 client, resilience, idempotent scheduled sync"
milestone "Phase 3 - Detection engine"                  "EARS, CUSUM, moving-average z, backtest with injected outbreaks"
milestone "Phase 4 - Alerts API and security"           "REST API, alert state machine, JWT resource server"
milestone "Phase 5 - Notifications"                     "Transactional outbox, signed webhooks, email"
milestone "Phase 6 - FHIR output"                       "DetectedIssue mapping and read-only FHIR endpoints"
milestone "Phase 7 - Hardening and v0.1.0"              "Observability, load test, packaging, first release"

echo "==> Rulesets"
upsert_ruleset() { # $1 = name, JSON on stdin
  local name="$1" body id
  body="$(cat)"
  id="$(gh api "repos/$REPO/rulesets" --jq ".[] | select(.name==\"$name\") | .id")"
  if [ -n "$id" ]; then
    gh api -X PUT "repos/$REPO/rulesets/$id" --input - <<<"$body" --silent
  else
    gh api -X POST "repos/$REPO/rulesets" --input - <<<"$body" --silent
  fi
}

upsert_ruleset "main protection" <<'JSON'
{
  "name": "main protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "pull_request", "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["merge"]
    } },
    { "type": "required_status_checks", "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          { "context": "build" },
          { "context": "container" },
          { "context": "commit-lint" },
          { "context": "codeql" }
        ]
    } }
  ]
}
JSON

upsert_ruleset "release tags are immutable" <<'JSON'
{
  "name": "release tags are immutable",
  "target": "tag",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["refs/tags/phase-*", "refs/tags/v*"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "update" }
  ]
}
JSON

echo "Done. Read the settings back with the commands in docs/repo-setup.md."
