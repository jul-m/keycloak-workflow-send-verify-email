# Contributing

*[Version française](fr/CONTRIBUTING.FR.md)*

Thanks for considering a contribution to this project. This document covers the local setup, coding conventions, and the pull request process.

## Prerequisites

- Java 17 (JDK)
- Maven (or use the `mvn` wrapper already resolved by your environment)

Docker is not required for local development. Integration tests default to Keycloak's `distribution` mode, which downloads and unpacks a real Keycloak release and runs it as a plain external process — no container involved, and none is used in CI either (see "Adding support for a new Keycloak version" below).

## Project layout

- `src/main/java` — the workflow step provider and its factory (`SendVerifyEmailStepProvider`, `SendVerifyEmailStepProviderFactory`)
- `src/main/resources/META-INF/services` — SPI registration file discovered by Keycloak at startup
- `src/test/java` — unit test (step metadata) and integration tests (`*IT.java`) built on Keycloak's official test-framework
- `scripts/build-tests.sh` — builds and validates (`mvn clean verify`) the provider against one Keycloak version, or every supported version with `all`; defaults to the version already declared in `pom.xml`. Accepts versions not yet listed in `kc-versions.sh` too (prints a warning), which is how `new-keycloak-version.yml` tests candidate versions.
- `scripts/kc-versions.sh` — single source of truth for the supported Keycloak version range
- `scripts/compute-next-version.sh` — prints the extension's next semver version from the latest published release plus a `minor`/`patch` bump; used by `ci.yml` on `feat/*`/`fix/*`/`kc-fix/*` branches (see "Versioning and releases" below)
- `scripts/render-readme-compat-table.sh` — regenerates the compatibility table in `README.md`/`README.fr.md` from `scripts/kc-versions.sh`
- `scripts/check-pom-kc-versions-sync.sh` — fails if `pom.xml`'s build version drifts from `scripts/kc-versions.sh`'s max supported version
- `docs/` — contributor and security documentation (English in `docs/`, French in `docs/fr/`)
- `docs/examples/` — ready-to-post workflow definitions referenced from the README
- `.github/workflows/` — CI, release, CodeQL, and `new-keycloak-version.yml` (scheduled detection of new Keycloak releases)

## Building and testing

The extension ships as a single JAR with its own semver (see `scripts/kc-versions.sh` for the Keycloak versions it supports — never encoded in the artifact's own version). `pom.xml` on `main` always compiles against the most recent supported Keycloak version.

Build and fully validate against a specific Keycloak version (unit + integration tests):

```bash
bash scripts/build-tests.sh 26.7
```

Without an argument, it validates against whatever `pom.xml` already declares (the current build version):

```bash
bash scripts/build-tests.sh
```

To validate against every Keycloak version listed in `scripts/kc-versions.sh` in one go — one `mvn clean verify` run per version, stopping at the first failure — pass `all`:

```bash
bash scripts/build-tests.sh all
```

Under the hood this always runs `mvn clean verify`, which covers both unit and integration tests in a single run and starts a real Keycloak server through Keycloak's [official test-framework](https://github.com/keycloak/keycloak/tree/main/test-framework) (Admin API, realms/users/workflows, a fake SMTP server to capture emails). The server mode is controlled by the `KC_TEST_SERVER` environment variable and defaults to `distribution` (a real, unpacked Keycloak release run as an external process — see "Prerequisites" above). You can also invoke Maven directly and select `embedded` mode, which boots Keycloak inside the same JVM as the tests — useful for local debugging (see "VS Code" below):

```bash
KC_TEST_SERVER=embedded mvn verify
```

Before opening a pull request, make sure `mvn verify` passes locally. The `revision` property in `pom.xml` must stay a plain literal (no `${...}` placeholder) — the test-framework's own Maven resolver requires it; the release workflow passes `-Dproject-version=...`/`-Drevision=...` explicitly instead.

### Test structure

All scenarios for the `send-verify-email` step live under `src/test/java/.../sendverifyemail/`:

- `SendVerifyEmailStep*Scenario.java` — one `abstract` class per scenario (default mode, custom message, client/redirect URI, reset-email-verification, user eligibility, scheduling), holding the actual `@Test` methods and assertions.
- `SendVerifyEmailStepIT.java` — the only class Maven/an IDE actually run. It wraps each scenario as a `@Nested @KeycloakIntegrationTest` subclass, with every `@Test` method redeclared as a one-line `@Override` calling `super.xxx()`.

Follow this pattern for new scenarios: add a `*Scenario.java` file, then wire it into `SendVerifyEmailStepIT.java`. Two rules matter and are easy to get wrong:
- The `@Override` redeclaration (not an empty `@Nested` body) is required for VS Code's Testing panel to see the test — it doesn't resolve `@Test` methods inherited from another file, even though Maven runs them fine either way.
- This structure is also what makes running the whole `SendVerifyEmailStepIT.java` file boot a **single** shared Keycloak server for every scenario (`LifeCycle.GLOBAL` needs one JVM, and VS Code's Test Runner launches one JVM per top-level class) instead of one per scenario.

### VS Code

`.vscode/settings.json` offers two `java.test.config` profiles: `tests-fast` (`distribution` mode, default) and `tests-with-debug` (`embedded` mode, for stepping through provider code with a debugger). Three gotchas:

- **Orphaned Keycloak process**: if a `tests-fast` run is killed mid-way (Stop button, debugger disconnect), the external Keycloak process can be left holding port 8080. Fix: `lsof -nP -iTCP:8080 -sTCP:LISTEN` then `kill <pid>`.
- **`KC_TEST_SERVER_REUSE` is broken on macOS** — don't use it. macOS's built-in `fuser` doesn't support the `-n tcp` syntax the test-framework relies on to check for a reusable server.
- **`tests-with-debug` (embedded) fails with `ERROR: The ForkJoinPool has been initialized with the wrong thread factory`**: this can start happening reproducibly (not just occasionally) after several file renames/reloads in the same VS Code session, even with no change to `pom.xml` or `.vscode/settings.json`. It's not a stale `bin/` output folder or a wrong JDK (check those first, they're quick to rule out — `rm -rf bin/`, and Java Projects panel → project → Classpath → JDK Runtime should show `JavaSE-17`). If both check out and the error persists, run **"Java: Clean Workspace Cache"** from the command palette and reload the window — this clears the Java Language Server's stale workspace state, which is the actual fix in that case.

## Coding conventions

- Keep the code in English, including comments, log messages, and commit messages.
- Favor small, focused changes. Avoid speculative abstractions or configuration options that aren't needed by the change at hand.
- Only add comments where the *why* isn't obvious from the code itself (a workaround, a non-obvious constraint, a subtle invariant). Don't restate what the code already says.
- New behavior should be covered by a new scenario (see "Test structure" above), following the existing test structure (see `support/AbstractSendVerifyEmailWorkflowTest.java`).
- Keep the step's config parameters and defaults aligned with the native Admin API (`UserResource.verifySendEmailParams`) wherever applicable, as documented in the README.

## Documentation

The documentation is bilingual, and each page has a counterpart that must be updated in the same pull request:

| English | French |
| --- | --- |
| `README.md` | `README.fr.md` |
| `docs/CONTRIBUTING.md` | `docs/fr/CONTRIBUTING.FR.md` |
| `docs/SECURITY.md` | `docs/fr/SECURITY.FR.md` |
| `CODE_OF_CONDUCT.md` | `CODE_OF_CONDUCT.fr.md` |
| `docs/examples/*.yml` | `docs/examples/*.fr.yml` |

`CHANGELOG.md` is English-only. Two more rules:

- The compatibility table in both READMEs sits between `<!-- kc-compat:start -->` / `<!-- kc-compat:end -->` markers and is generated — edit `scripts/kc-versions.sh` and run `bash scripts/render-readme-compat-table.sh` instead of editing it by hand.
- Any change to the step's config parameters, template variables, defaults or behavior must be reflected in both READMEs and in `CHANGELOG.md` under `[Unreleased]`.

## Versioning and releases

Releases are triggered by merging a pull request from a branch with one of these prefixes — there is no separate manual release step for a normal change:

| Branch prefix | Bump | Use for |
| --- | --- | --- |
| `feat/*` | minor (`X.Y.0`) | New functionality |
| `fix/*` | patch (`X.Y.Z`) | Bug fixes |
| `kc-fix/*` | patch (`X.Y.Z`) | Fixing a Keycloak compatibility issue (see "Adding support for a new Keycloak version" below) |

`ci.yml` computes the next version from the latest published release (`scripts/compute-next-version.sh`) and commits it into `pom.xml`'s `project-version`/`revision` — you don't need to bump the version yourself. Merging the PR then triggers `release.yml`, which builds, tags and publishes the release automatically, using whatever version `ci.yml` already committed.

A `kc-support/*` branch (opened automatically by `new-keycloak-version.yml`, see below) is different: it doesn't bump the extension's version or trigger a release, since no code changed — merging it only extends the *already-published* latest release's documented compatibility range.

### If you're contributing from a fork

`ci.yml` commits the version bump directly onto your branch — which needs write access GitHub denies to checks running on a pull request from a fork. Only `ci.yml` needs to run on your fork to unblock this (the repository's other workflows don't apply to forks): enable Actions there if it isn't already (**Settings → Actions**, or the banner offered the first time you open the Actions tab). Once enabled, each push to your branch triggers `ci.yml` there with full write access, which commits the version bump for you. If the check here fails saying the branch is out of date, that's exactly the signal to let `ci.yml` run on your fork, then push again.

## Adding support for a new Keycloak version

`scripts/kc-versions.sh`'s `SUPPORTED_KC_VERSIONS` array is the single source of truth for the extension's Keycloak compatibility range — it must stay a contiguous list of minor versions (no gaps), and its last entry must always match `pom.xml`'s build version (`scripts/check-pom-kc-versions-sync.sh` enforces this in CI).

To extend the range with a newer version:

1. Bump `keycloak.version` in `pom.xml` to the new version's `.0` patch (e.g. `26.8.0`).
2. Add the version to `SUPPORTED_KC_VERSIONS` in `scripts/kc-versions.sh` (append at the end).
3. Run `bash scripts/build-tests.sh all` to validate every version now listed in `scripts/kc-versions.sh`, including the new one, and fix any API incompatibility.
4. Run `bash scripts/render-readme-compat-table.sh` to refresh the compatibility table in `README.md`/`README.fr.md`.

If a listed version becomes incompatible and can't be fixed with a change that still works across the whole range, remove it from `scripts/kc-versions.sh` instead — removing anything other than the lowest version means removing everything below it too, to keep the list gap-free. Users on a removed version need to stay on a previous extension release or upgrade Keycloak.

New Keycloak releases beyond the current max are normally detected automatically by `.github/workflows/new-keycloak-version.yml` (scheduled weekly, or triggered manually via `workflow_dispatch`, optionally forcing `keycloak_version`). It runs the same real integration-test suite described above (`scripts/build-tests.sh <version>`) against the candidate version — not a smoke test — and opens a pull request rather than pushing to `main` directly:

- **Tests pass** — opens a PR from a `kc-support/<version>` branch that bumps `pom.xml`, appends the version to `scripts/kc-versions.sh`, and refreshes both READMEs; no code changes. Merging it only extends the compatibility range documented on the *already-published* latest release's notes — no rebuild, no new release, since the current code already works on that version.
- **Tests fail** — opens a draft PR from a `kc-fix/<version>` branch with the same version-list bump, plus a tracking issue that the PR closes on merge. CI on that branch is expected to stay red until someone actually fixes the incompatibility (a runtime-reflection workaround if only a method signature changed, or dropping the version from `scripts/kc-versions.sh` again if it doesn't — see "If a listed version becomes incompatible" above). Because merging it is a real code change, it's versioned and released exactly like a `fix/*` branch (see "Versioning and releases" above) — no bespoke version math.

That workflow has not yet been exercised end-to-end against a real new Keycloak minor version: until it has, trigger it manually via `workflow_dispatch` and check its output before relying on it.

## Pull requests

1. Fork the repository and create a branch from `main`, named `feat/<short-description>` or `fix/<short-description>` depending on the change (see "Versioning and releases" above).
2. Make your change, with tests covering new behavior.
3. Make sure `mvn verify` passes (covers both unit and integration tests), and that CI is green.
4. Open a pull request describing the change and its motivation. Link any related issue.
5. Be responsive to review feedback — small, incremental follow-up commits are preferred over force-pushed rewrites during review.

## Reporting bugs and requesting features

Use the GitHub issue templates. Search existing issues first to avoid duplicates.

## Security issues

Do not open a public issue for a security vulnerability. See [SECURITY.md](SECURITY.md).

## Code of conduct

This project follows the [Code of Conduct](../CODE_OF_CONDUCT.md). By participating, you agree to abide by it.
