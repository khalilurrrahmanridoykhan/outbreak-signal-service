# Contributing

Thank you for considering a contribution. This is a small project with one
maintainer, so a short conversation before a large change saves everyone time.

## Before you start

- **Questions and ideas:** open a [Discussion](https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/discussions).
- **Bugs and concrete feature requests:** open an issue using the templates.
- **Security problems:** never in a public issue. See [SECURITY.md](SECURITY.md).
- **Data:** never post real patient or case-level data anywhere in this
  repository, in issues, or in pull requests. Synthetic or public demo data only.

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Development setup

You need JDK 25, Docker, and nothing else: Maven is provided by the wrapper.

```bash
git clone https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service.git
cd outbreak-signal-service
git config core.hooksPath .githooks   # enables the commit-msg check, once per clone
./mvnw verify                          # builds and runs every test
docker compose up --build              # runs the app and Postgres on localhost:8080
```

Tests start a real PostgreSQL in a container through Testcontainers, so Docker
must be running. If you use Colima instead of Docker Desktop, tell Testcontainers
where the socket is:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Workflow

1. Branch from `main` (`git switch -c short-descriptive-name`).
2. Make small commits, one concern each.
3. Run `./mvnw verify` before pushing.
4. Open a pull request against `main` and fill in the template.

`main` is protected: changes arrive through pull requests with passing checks
and are merged with a merge commit (no squash, no rebase), so the branch history
stays readable.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): a type, an
optional scope, and a short imperative summary.

```
feat(detection): add EARS C2
fix(sync): retry only on 5xx responses
docs: explain the alert lifecycle
test(alert): cover concurrent acknowledge
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.

Commit messages in this repository must not contain `Co-Authored-By` trailers or
attribution to coding assistants. The `commit-msg` hook and the `commit-lint`
check reject them.

## Code and tests

- Schema changes are **Flyway migrations only** (`src/main/resources/db/migration`).
  Never edit a migration that has been merged; add a new one.
- New behaviour needs tests. Prefer real PostgreSQL through Testcontainers over
  in-memory substitutes.
- Keep pure logic (detection algorithms, period handling) free of Spring so it can
  be unit tested on small, hand-checkable inputs.
- Do not add a dependency without saying why in the pull request.
- Record non-obvious decisions in [DECISIONS.md](DECISIONS.md) and user-visible
  changes under "Unreleased" in [CHANGELOG.md](CHANGELOG.md).

## Licensing

By contributing you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), the same as the rest of the project.
