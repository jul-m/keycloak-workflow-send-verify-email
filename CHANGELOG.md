# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The extension's version is its own and never encodes a Keycloak version: a single JAR per release
supports a range of Keycloak versions, listed in [`scripts/kc-versions.sh`](scripts/kc-versions.sh), in
the release notes, and in the [README compatibility table](README.md#compatibility).

## [Unreleased]

## [0.1.0] - Unreleased

Initial release.

### Added

- `send-verify-email` workflow step, sending a signed email-verification action link to the workflow
  user. Applies to user resources and is usable from any workflow trigger (event-based, scheduled or
  ad hoc).
- Default sending mode delegating to `EmailTemplateProvider.sendVerifyEmail`, producing the same
  email as the native Admin API `send-verify-email`.
- Custom-message mode (`message`/`subject`) rendering the workflow notification template, with
  `${link}`, `${user.*}`, `${realm.*}` and `${realmFullBaseUrl}` variable substitution.
- `client_id`, `redirect_uri`, `lifespan` and `reset_email_verification` configuration parameters.
  User, client and redirect-URI validation, and all default values, are delegated to
  `UserResource.verifySendEmailParams` to stay aligned with the native Admin API.
- Fallback to the realm's `frontendUrl` when the step runs outside of an HTTP request (for example on
  a scheduled trigger), with an explicit error when the realm has none.
- Unit test for the step's metadata, and integration tests on Keycloak's official test-framework
  covering every configuration parameter, user eligibility and scheduling.
- `scripts/kc-versions.sh` as the single source of truth for the supported Keycloak version range,
  kept in sync with `pom.xml` and with the README compatibility table by `scripts/`.
- CI workflow building and testing every supported Keycloak version, a release workflow publishing
  the JAR and its checksum to GitHub Releases, and a scheduled workflow that validates newly released
  Keycloak versions against the already-published JAR without rebuilding it.

[Unreleased]: https://github.com/jul-m/keycloak-workflow-send-verify-email/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/jul-m/keycloak-workflow-send-verify-email/releases/tag/v0.1.0
