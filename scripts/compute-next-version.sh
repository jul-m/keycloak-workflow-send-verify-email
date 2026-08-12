#!/bin/bash
#
# Prints the extension's next semver version (X.Y.Z) to stdout, computed from the
# latest published GitHub release plus a bump type - used by ci.yml to enforce/commit
# the version on feat/*, fix/* and kc-fix/* branches (see docs/CONTRIBUTING.md).
#
# Usage: compute-next-version.sh <minor|patch>
#   minor - feat/* branches: bump the middle digit, reset the patch digit to 0.
#   patch - fix/*, kc-fix/* branches: bump the last digit.
#
# Baseline: the latest published release tag (any prerelease suffix is dropped before
# bumping). Falls back to pom.xml's <project-version> if no release exists yet.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
POM_FILE="$REPO_ROOT/pom.xml"

BUMP="${1:-}"
if [[ "$BUMP" != "minor" && "$BUMP" != "patch" ]]; then
	echo "Usage: $0 <minor|patch>" >&2
	exit 2
fi

if base=$(gh release view --json tagName --jq .tagName 2>/dev/null); then
	base="${base#v}"
	base="${base%%-*}" # drop any prerelease suffix, e.g. 0.1.0-rc.1 -> 0.1.0
else
	base=$(grep -m1 -oE '<project-version>[0-9]+\.[0-9]+\.[0-9]+</project-version>' "$POM_FILE" \
		| grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
fi

IFS='.' read -r major minor patch <<< "$base"

if [[ "$BUMP" == "minor" ]]; then
	echo "$major.$((minor + 1)).0"
else
	echo "$major.$minor.$((patch + 1))"
fi
