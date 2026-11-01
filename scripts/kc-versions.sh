#!/bin/bash
#
# Single source of truth for the extension's supported Keycloak version range
# (the SUPPORTED_KC_VERSIONS array below). Invariants:
#   - Keep values quoted, matching the XX.Y minor-version format.
#   - Must be a contiguous list of Keycloak minor versions (no gaps), sorted ascending.
#     First entry = minimum supported version. Last entry = maximum supported version.
#   - The last entry MUST match the major.minor of keycloak.version in pom.xml (a full
#     patch, e.g. 26.7.0; main always compiles/ships against the most recent supported
#     minor - scripts/check-pom-kc-versions-sync.sh checks this invariant).
#   - Every entry is re-tested and validated on each release (scripts/build-tests.sh <version>).
#     A version that becomes incompatible and can't be fixed is removed here rather than
#     shipped broken; removing an old version means users on it must stay on a previous
#     extension release or upgrade Keycloak. Removing a version other than the lowest one
#     requires also removing everything below it, to keep the list gap-free.
#   - New Keycloak releases are proposed via a pull request (not pushed directly) by
#     .github/workflows/new-keycloak-version.yml, once real integration tests pass
#     against the candidate version.
#
# Usage: kc-versions.sh [list|min|max|range]
#   list  - one supported version per line (default)
#   min   - lowest supported version
#   max   - highest supported version (must match pom.xml's build version)
#   range - human-readable summary, e.g. "26.4 - 26.7" or "26.7" for a single version

set -euo pipefail

SUPPORTED_KC_VERSIONS=("26.7")

list_versions() {
	printf '%s\n' "${SUPPORTED_KC_VERSIONS[@]}"
}

COMMAND="${1:-list}"

case "$COMMAND" in
	list)
		list_versions
		;;
	min)
		list_versions | head -n1
		;;
	max)
		list_versions | tail -n1
		;;
	range)
		versions="$(list_versions)"
		count=$(printf '%s\n' "$versions" | wc -l | tr -d ' ')
		min=$(printf '%s\n' "$versions" | head -n1)
		max=$(printf '%s\n' "$versions" | tail -n1)
		if [[ "$count" -le 1 ]]; then
			echo "$max"
		else
			echo "$min - $max"
		fi
		;;
	*)
		echo "Usage: $0 [list|min|max|range]" >&2
		exit 2
		;;
esac