#!/usr/bin/env bash
#
# Category 2: restores every committed backup into a fresh install of the build under test on a
# connected emulator, and checks each one arrives intact.
#
# Usage: scripts/run-restore-test.sh <new-qa.apk> [output-dir]
#
# Order, one `am instrument` call per step, each followed by a backup the host compares with the
# file restored:
#   1. sign up, set the backup folder, turn auto-backup off;
#   2. restore upgrade-test/fixtures/format-1.bak, format-2.bak, … then upgrade-test/seed/seed.bak;
#      the first through the empty records screen, the rest through the Backup & Restore tab;
#   3. round trip: restore N's own backup of the seed, which proves N's writer and reader agree;
#   4. a damaged copy of the seed and a wrong password must both be refused, and the backup taken
#      afterwards must equal the round trip's: a failed restore must not touch the vault.
# Build the harness first: ./gradlew :upgrade-test:assembleDebug
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/backup-files.sh
source "$script_dir/lib/backup-files.sh"
# shellcheck source=lib/instrumentation-guard.sh
source "$script_dir/lib/instrumentation-guard.sh"
# shellcheck source=lib/crash-sentinel.sh
source "$script_dir/lib/crash-sentinel.sh"
# shellcheck source=lib/harness.sh
source "$script_dir/lib/harness.sh"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "usage: $0 <new-qa.apk> [output-dir]" >&2
    exit 2
fi
new_apk="$1"
if [ ! -f settings.gradle ]; then
    echo "error: run this from the repository root." >&2
    exit 2
fi
harness_init "${2:-upgrade-test-out/restore}"

# Names in Downloads carry a prefix so they cannot be confused with anything else there.
DEVICE_PREFIX="restore-test-"
DAMAGED_BYTES=2048

[ -s "$new_apk" ] || { echo "error: $new_apk is missing or empty." >&2; exit 1; }
assert_qa_apk "$new_apk"

# The files to restore, oldest format first, then the seed. backup-format-test.sh guarantees
# format-1 … format-(V-1) all exist, so counting up finds every one.
files=""
k=1
while [ -e "$FIXTURES_DIR/format-$k.bak" ]; do
    files="$files $FIXTURES_DIR/format-$k.bak"
    k=$((k + 1))
done
files="$files $SEED_FILE"
damaged="$HARNESS_OUT/damaged.bak"
head -c "$DAMAGED_BYTES" "$SEED_FILE" > "$damaged"

echo "== Install N =="
clean_slate
adb install "$new_apk" > /dev/null
grant_notifications
install_harness
reset_backup_dir
for file in $files; do
    push_to_downloads "$file" "$DEVICE_PREFIX$(basename "$file")"
done
push_to_downloads "$damaged" "${DEVICE_PREFIX}damaged.bak"

run_phase setup RestoreTest#setUpFreshInstall -e backupDir "$BACKUP_DIR"

# Restores one file, runs the screen checks and a backup on the device, then compares that backup
# with the file.
#
# $1 - local file, $2 - its name in Downloads, $3 - its password, $4 - test method, $5 - label
restore_and_compare() {
    empty_backup_dir
    run_phase "$5" "RestoreTest#$4" \
        -e restoreFile "$2" \
        -e restorePassword "$3" \
        -e expectedRows "$(expected_rows_argument "$1" "$3")"
    pull_single_backup "$HARNESS_OUT/after-$5.bak"
    assert_same_records "$1" "$3" "$HARNESS_OUT/after-$5.bak" "$5"
}

method=restoreIntoEmptyVault
for file in $files; do
    name=$(basename "$file" .bak)
    password=$(password_for_format "$(inspect_backup --header "$file")")
    restore_and_compare "$file" "$DEVICE_PREFIX$name.bak" "$password" "$method" "$name"
    method=restoreOverVault
done

push_to_downloads "$HARNESS_OUT/after-seed.bak" "${DEVICE_PREFIX}round-trip.bak"
restore_and_compare "$HARNESS_OUT/after-seed.bak" "${DEVICE_PREFIX}round-trip.bak" \
    "$FIXED_BACKUP_PASSWORD" restoreOverVault round-trip

empty_backup_dir
run_phase refusals RestoreTest#failedRestoresLeaveVaultUntouched \
    -e damagedFile "${DEVICE_PREFIX}damaged.bak" \
    -e restoreFile "${DEVICE_PREFIX}seed.bak"
pull_single_backup "$HARNESS_OUT/after-refusals.bak"
assert_same_records "$HARNESS_OUT/after-round-trip.bak" "$FIXED_BACKUP_PASSWORD" \
    "$HARNESS_OUT/after-refusals.bak" refusals

echo "== Restore test passed =="
