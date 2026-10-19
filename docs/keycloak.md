# Trying the secured API with a real token

The default `docker compose up` runs with no issuer available, so every
`/api/v1/**` call returns 401 (confirmed — see `DECISIONS.md`). To get a real
JWT and call the API as `VIEWER`, `ANALYST` or `ADMIN`, bring up the optional
Keycloak profile instead:

```bash
docker compose --profile keycloak up --build
```

This also starts a `keycloak` service (realm, roles and three test users
pre-imported from `keycloak/realm-export.json`) and points the app at it. None
of this is for anything but local trial — see the warnings in
`docker-compose.yml` and `DECISIONS.md`.

## Test users

| Username | Password | Role |
|---|---|---|
| `viewer1` | `viewer1-pass` | `VIEWER` |
| `analyst1` | `analyst1-pass` | `ANALYST` |
| `admin1` | `admin1-pass` | `ADMIN` |

## Minting a token

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8081/realms/outbreak-signal/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d grant_type=password \
  -d client_id=outbreak-signal-service \
  -d username=analyst1 \
  -d password=analyst1-pass \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")
```

This is the Resource Owner Password Credentials grant — fine for a local,
non-interactive test fixture with a public client and no real users, never
appropriate for a real application (it is deprecated in OAuth 2.1 for exactly
that reason). A real client authenticates however its own identity provider
expects.

## Calling the API

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/alerts
curl -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/alerts/1/acknowledge
```

A `VIEWER` token gets `403` on the second call; an `ANALYST` or `ADMIN` token
succeeds. `POST /api/v1/sync` and `POST /api/v1/detection/run` need `ADMIN`.

## Verified end to end

Run once, for real, while building this phase: with the Keycloak profile up,
a real sync against the live DHIS2 demo (152 districts, 495 series), real
detection (958 alerts across five algorithms), then `viewer1` listing them,
`analyst1` being refused a direct `resolve` on an `OPEN` alert (`409`,
carrying `from`/`to`), acknowledging it instead, and dismissing another with
a reason — all against real Postgres data and real Keycloak-issued tokens,
not mocks.

## Two things that were not obvious while setting this up

- **Keycloak 26's declarative User Profile requires `firstName` and
  `lastName`.** Without them, the password grant fails with the unhelpful
  `"Account is not fully set up"` / `resolve_required_actions` — nothing in
  the user's own `enabled`, `emailVerified` or `requiredActions` fields
  explains why. `realm-export.json`'s three users all set both.
- **The app reaches Keycloak through Compose's internal network, not
  `localhost`.** `localhost` inside the `app` container means the container
  itself; `docker-compose.yml` points the app at `http://keycloak:8080/...`
  (the internal service name and port), while *your own* `curl` calls from
  the host use `http://localhost:8081/...` (the mapped port). Mixing these up
  looks like a token validation failure (a blanket `401` on every call, token
  or no token) but is really a DNS/networking one.
