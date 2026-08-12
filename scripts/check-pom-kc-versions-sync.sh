#!/bin/bash
#
# Fails if pom.xml's build version (keycloak.version) has drifted from kc-versions.sh's max
# supported version. main's pom.xml must always compile/ship against the most recent
# supported Keycloak minor version, see kc-versions.sh's header comment. keycloak.version
# holds a full patch (e.g. 26.7.2); only its major.minor is compared against kc-versions.sh,
# which only tracks minors - bumping the patch alone is not a kc-versions.sh change.
#
# Usage: check-pom-kc-versions-sync.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
POM_FILE="$REPO_ROOT/pom.xml"

POM_PATCH=$(grep -m1 -oE '<keycloak\.version>[0-9]+\.[0-9]+\.[0-9]+</keycloak\.version>' "$POM_FILE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
POM_VERSION="${POM_PATCH%.*}"
KC_MAX="$("$SCRIPT_DIR/kc-versions.sh" max)"

if [[ "$POM_VERSION" != "$KC_MAX" ]]; then
	echo "::error::pom.xml targets Keycloak $POM_VERSION ($POM_PATCH) but kc-versions.sh's max supported version is $KC_MAX - keep them in sync." >&2
	exit 1
fi

echo "pom.xml build version ($POM_PATCH) matches kc-versions.sh's max supported version ($KC_MAX)."
