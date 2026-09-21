# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Spring Boot 4.1 project on Java 25 with Maven.
- PostgreSQL schema managed by Flyway: organisation units, weekly series and
  observations, with constraints that reject duplicate weeks, non-Monday week
  starts and negative values.
- Typed, validated settings under `outbreak.*`.
- RFC 9457 problem responses that do not leak internal details.
- Dockerfile and Compose stack (application and PostgreSQL).
- Continuous integration: build and tests, container smoke test, CodeQL,
  dependency review, and a commit-message check.
- Community and governance files, issue forms and a pull request template.
