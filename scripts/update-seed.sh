#!/usr/bin/env bash
#
# Installs a newly captured backup as the upgrade-test seed, and archives the outgoing seed as a
# fixture when the format has changed.
#
# Usage: scripts/update-seed.sh <captured.bak> "<captured from>"
#   <captured from> names the build, e.g. "debug build of PR #281 (a1b2c3d)"
#
# What it does, in order, stopping at the first problem:
#   1. Refuses a file that is not in the current format (BACKUP_VERSION) or does not decode with
#      the fixed backup password.
#   2. If the current seed is an older format k, copies it to upgrade-test/fixtures/format-<k>.bak
#      and records its provenance in the fixtures README, so the restore test keeps covering it.
#   3. Copies the new file to upgrade-test/seed/seed.bak.
#   4. Rewrites the seed README's provenance lines.
#
# The capture procedure is in docs/testing/upgrade-harness-operations.md#capturing-a-seed.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
cd "$repo_root"
# shellcheck source=lib/backup-files.sh
source "$script_dir/lib/backup-files.sh"

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <captured.bak> \"<captured from>\"" >&2
    exit 2
fi
captured="$1"
captured_from="$2"
today=$(date +%Y-%m-%d)

version=$(backup_version_in_code)
captured_version=$(inspect_backup --header "$captured")
if [ "$captured_version" != "$version" ]; then
    echo "error: $captured is format $captured_version, but BACKUP_VERSION is $version." >&2
    echo "       Capture it on a build of this branch." >&2
    exit 1
fi
if ! inspect_backup --canonical "$captured" "$FIXED_BACKUP_PASSWORD" > /dev/null; then
    echo "error: $captured does not decode with the fixed backup password $FIXED_BACKUP_PASSWORD." >&2
    exit 1
fi

# Step 2: archive the outgoing seed when the format moved on.
if [ -s "$SEED_FILE" ]; then
    old_version=$(inspect_backup --header "$SEED_FILE")
    if [ "$old_version" != "$version" ]; then
        archive="$FIXTURES_DIR/format-$old_version.bak"
        if [ -e "$archive" ] && ! cmp -s "$SEED_FILE" "$archive"; then
            echo "error: $archive already exists and differs from the outgoing seed." >&2
            echo "       Old-format files are never replaced; resolve this by hand." >&2
            exit 1
        fi
        cp "$SEED_FILE" "$archive"
        old_from=$(sed -n 's/^- Captured from: //p' "$SEED_README")
        old_on=$(sed -n 's/^- Captured on: //p' "$SEED_README")
        row="| \`format-$old_version.bak\` | $old_from | $old_on | \`$FIXED_BACKUP_PASSWORD\` |"
        row="$row" awk '/^<!-- provenance rows end -->$/ { print ENVIRON["row"] } { print }' \
            "$FIXTURES_README" > "$FIXTURES_README.tmp"
        mv "$FIXTURES_README.tmp" "$FIXTURES_README"
        echo "archived the format-$old_version seed as $archive"
    fi
fi

# Step 3: install.
mkdir -p "$(dirname "$SEED_FILE")"
cp "$captured" "$SEED_FILE"

# Step 4: provenance. Step 2 reads these lines back on the next format change.
{
    echo "# Upgrade-test seed"
    echo
    echo "The backup the upgrade test restores into N-1 when this commit's release is N-1, and the"
    echo "current-format file the restore test restores into N. Replace it only with"
    echo "\`scripts/update-seed.sh\`; the procedure is in"
    echo "[upgrade-harness-operations.md](../../docs/testing/upgrade-harness-operations.md#capturing-a-seed)."
    echo
    echo "- Captured from: $captured_from"
    echo "- Captured on: $today"
    echo "- Backup password: \`$FIXED_BACKUP_PASSWORD\`"
    echo
    echo "To see what it holds: \`java scripts/InspectBackup.java upgrade-test/seed/seed.bak $FIXED_BACKUP_PASSWORD\`."
} > "$SEED_README"

echo "installed $SEED_FILE (format $version). Review the diff and commit."
