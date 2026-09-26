#!/usr/bin/env bash
#
# PR-time checks that keep the backup format, the committed backup files and the decoder in step.
#
#   1. The export classes (the backup's JSON shape) match the entry for BACKUP_VERSION in
#      app/backup-format.lock, and no existing lock entry was edited. Changing the classes
#      therefore forces a BACKUP_VERSION bump and a new lock line in the same PR.
#   2. upgrade-test/seed/seed.bak is in the current format and decodes with the fixed password.
#   3. scripts/InspectBackup.java supports the current format.
#   4. Every older format k has upgrade-test/fixtures/format-<k>.bak, in format k, decodable.
#
# Why it matters: the upgrade and restore tests restore these files and compare decoded backups.
# A format change without a new seed would leave the upgrade test restoring stale data, and a
# missing old-format file would drop a format users still hold from the restore test.
#
# Every failing check is reported in one run. Plain bash + JDK, a few seconds, no emulator. Run
# by ci.yml on every PR and by the pre-commit hook when a relevant file is staged.
#
# BACKUP_FORMAT_BASE_REF (optional) names the commit to compare the lock against for the
# append-only rule: CI passes the PR's base branch; the hook passes HEAD. Unset skips that rule.
#
# Run from the repo root: scripts/tests/backup-format-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/backup-files.sh
source "$script_dir/../lib/backup-files.sh"

failures=0
report() {
    echo "FAIL: $1" >&2
    failures=$((failures + 1))
}

version=$(backup_version_in_code)
if ! printf '%s' "$version" | grep -qE '^[0-9]+$'; then
    echo "error: could not read BACKUP_VERSION from $COMMON_CONSTANTS" >&2
    exit 1
fi
echo "BACKUP_VERSION in code: $version"

# Check 1: export classes vs the lock.
current_hash=$(export_classes_hash)
locked_hash=$(sed -n "s/^$version=\([0-9a-f]\{64\}\)$/\1/p" "$BACKUP_FORMAT_LOCK" 2> /dev/null)
if [ -z "$locked_hash" ]; then
    report "$BACKUP_FORMAT_LOCK has no line for BACKUP_VERSION $version. Append: $version=$current_hash"
elif [ "$locked_hash" != "$current_hash" ]; then
    report "the export classes in $EXPORT_CLASSES_DIR changed, so the backup format changed.
      Bump BACKUP_VERSION in $COMMON_CONSTANTS and append a line for the new version:
        <new version>=$current_hash
      Then capture a new seed (docs/testing/upgrade-harness-operations.md#capturing-a-seed)."
fi
duplicates=$(grep -E '^[0-9]+=' "$BACKUP_FORMAT_LOCK" 2> /dev/null | cut -d= -f2 | sort | uniq -d)
if [ -n "$duplicates" ]; then
    report "$BACKUP_FORMAT_LOCK records the same export-class hash for two versions: a version was bumped without a format change, or a line was copied."
fi
base_ref="${BACKUP_FORMAT_BASE_REF:-}"
if [ -n "$base_ref" ]; then
    if base_lock=$(git show "$base_ref:$BACKUP_FORMAT_LOCK" 2> /dev/null); then
        while IFS= read -r line; do
            case "$line" in
                [0-9]*=*)
                    if ! grep -qxF "$line" "$BACKUP_FORMAT_LOCK"; then
                        report "$BACKUP_FORMAT_LOCK is append-only, but '$line' from $base_ref was changed or removed."
                    fi
                    ;;
            esac
        done <<< "$base_lock"
    else
        echo "note: $base_ref has no $BACKUP_FORMAT_LOCK; append-only rule skipped"
    fi
fi

# Check 2: the seed.
if [ ! -s "$SEED_FILE" ]; then
    report "$SEED_FILE is missing."
else
    seed_version=$(inspect_backup --header "$SEED_FILE" 2>&1)
    if [ "$seed_version" != "$version" ]; then
        report "$SEED_FILE is format '$seed_version' but BACKUP_VERSION is $version.
      Capture a new seed on this PR's build and install it with scripts/update-seed.sh."
    elif ! inspect_backup --canonical "$SEED_FILE" "$FIXED_BACKUP_PASSWORD" > /dev/null; then
        report "$SEED_FILE does not decode with the fixed password $FIXED_BACKUP_PASSWORD."
    fi
fi

# Check 3: the decoder.
supported=$(inspect_backup --supported-version)
if [ "$supported" -lt "$version" ]; then
    report "scripts/InspectBackup.java supports up to format $supported, but BACKUP_VERSION is $version.
      Teach it the new format and raise SUPPORTED_BACKUP_VERSION."
fi

# Check 4: one frozen file per older format.
k=1
while [ "$k" -lt "$version" ]; do
    fixture="$FIXTURES_DIR/format-$k.bak"
    if [ ! -s "$fixture" ]; then
        report "$fixture is missing. Every format users may still hold needs a file;
      scripts/update-seed.sh archives the outgoing seed automatically on a format bump."
    else
        fixture_version=$(inspect_backup --header "$fixture" 2>&1)
        if [ "$fixture_version" != "$k" ]; then
            report "$fixture is format '$fixture_version', expected $k."
        elif ! inspect_backup --canonical "$fixture" "$(password_for_format "$k")" > /dev/null; then
            report "$fixture does not decode with its password."
        fi
    fi
    k=$((k + 1))
done

if [ "$failures" -gt 0 ]; then
    echo "$failures backup-format check(s) failed." >&2
    exit 1
fi
echo "backup-format checks passed"
