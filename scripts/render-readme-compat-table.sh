#!/bin/bash
#
# Regenerates the Keycloak compatibility table in README.md/README.fr.md from
# scripts/kc-versions.sh, between the <!-- kc-compat:start --> / <!-- kc-compat:end -->
# markers. Run after kc-versions.sh changes (release.yml, new-keycloak-version.yml) so
# the README never drifts out of sync with the actual supported range.
#
# Usage: render-readme-compat-table.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_URL="https://github.com/jul-m/keycloak-workflow-send-verify-email"

MIN="$("$SCRIPT_DIR/kc-versions.sh" min)"
MAX="$("$SCRIPT_DIR/kc-versions.sh" max)"

if [[ "$MIN" == "$MAX" ]]; then
	RANGE_LABEL="${MIN}.x"
else
	RANGE_LABEL="${MIN}.x - ${MAX}.x"
fi

# Plain multi-line strings, not heredocs: an apostrophe inside a heredoc nested in a
# $(...) command substitution breaks bash's parser (known quirk), and TABLE_FR needs one.
TABLE_EN="| Keycloak | Extension |
| --- | --- |
| ${RANGE_LABEL} | Latest release (see [Releases](${REPO_URL}/releases) for older extension versions and their supported range) |"

TABLE_FR="| Keycloak | Extension |
| --- | --- |
| ${RANGE_LABEL} | Dernière release (voir les [Releases](${REPO_URL}/releases) pour les versions antérieures de l'extension et leur plage de compatibilité) |"

update_file() {
	local file="$1"
	local table="$2"
	python3 - "$file" "$table" <<'PY'
import re
import sys
import pathlib

file_path, table = sys.argv[1], sys.argv[2]
path = pathlib.Path(file_path)
content = path.read_text()

pattern = re.compile(
    r"(<!-- kc-compat:start -->\n).*?(\n<!-- kc-compat:end -->)",
    re.DOTALL,
)
if not pattern.search(content):
    raise SystemExit(f"kc-compat markers not found in {file_path}")

new_content = pattern.sub(lambda m: m.group(1) + table + m.group(2), content)
path.write_text(new_content)
PY
}

update_file "$REPO_ROOT/README.md" "$TABLE_EN"
update_file "$REPO_ROOT/README.fr.md" "$TABLE_FR"
