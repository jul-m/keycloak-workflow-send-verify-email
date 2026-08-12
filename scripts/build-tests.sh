#!/bin/bash
#
# Builds and validates the extension against a given Keycloak minor version.
#
# Usage: build-tests.sh [keycloak-minor-version|all] [maven-args...]
#   keycloak-minor-version - e.g. "26.5". Defaults to whatever pom.xml already declares
#                            (the build version, i.e. the latest entry in kc-versions.sh).
#                            Versions not (yet) listed in kc-versions.sh are accepted too
#                            (e.g. to try a brand-new Keycloak release as a candidate) -
#                            a warning is printed, but the build still runs.
#   all                     - validates against every version listed in kc-versions.sh, one
#                             `mvn clean verify` run per version, stopping at the first
#                             failure (same validation .github/workflows/release.yml runs
#                             before a release).
#   maven-args              - extra arguments passed through to Maven as-is, e.g.
#                             -Dproject-version=0.2.0 -Drevision=0.2.0 (or 0.2.0-rc.1 for a
#                             pre-release)
#
# Always runs `mvn clean verify` (unit + integration tests) against a single Keycloak
# version, except with "all" which runs it once per version listed in
# scripts/kc-versions.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

VERSION="${1:-}"
MAVEN_ARGS=()
if (( $# > 1 )); then
	MAVEN_ARGS=("${@:2}")
fi

if [[ "$VERSION" == "all" ]]; then
	while IFS= read -r kc_version; do
		echo "== Building and testing against Keycloak $kc_version =="
		"$SCRIPT_DIR/build-tests.sh" "$kc_version" "${MAVEN_ARGS[@]}"
	done < <("$SCRIPT_DIR/kc-versions.sh" list)
	exit 0
fi

MAVEN_OPTIONS=()
if [[ -n "$VERSION" ]]; then
	if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+$ ]]; then
		echo "Invalid Keycloak version: $VERSION (expected format: XX.Y, or \"all\")" >&2
		exit 2
	fi
	if ! "$SCRIPT_DIR/kc-versions.sh" list | grep -qx "$VERSION"; then
		echo "Warning: Keycloak $VERSION is not listed in kc-versions.sh's SUPPORTED_KC_VERSIONS - testing it as a candidate version." >&2
	fi
	# Validates against the .0 patch of the given minor - kc-versions.sh only tracks minors,
	# and the .0 release is always the first one to exist for a given minor.
	MAVEN_OPTIONS=(-Dkeycloak.version="${VERSION}.0")
fi

MAVEN_CMD=(mvn --batch-mode --no-transfer-progress clean verify)
if (( ${#MAVEN_OPTIONS[@]} > 0 )); then
	MAVEN_CMD+=("${MAVEN_OPTIONS[@]}")
fi
if (( ${#MAVEN_ARGS[@]} > 0 )); then
	MAVEN_CMD+=("${MAVEN_ARGS[@]}")
fi

"${MAVEN_CMD[@]}"
