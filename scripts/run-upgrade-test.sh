#!/usr/bin/env bash
#
# Category 1: upgrades the previous release (N-1) in place to the build under test (N) on a
# connected emulator, and checks nothing was lost.
#
# Usage: scripts/run-upgrade-test.sh <previous-qa.apk> <previous-seed.bak> <new-qa.apk> [output-dir]
#
#   <previous-seed.bak>  the seed as committed at N-1's tag (`git show <tag>:upgrade-test/seed/seed.bak`),
#                        or the fallback file for a release that predates the seed; see
#                        scripts/resolve-previous-release.sh. Its password follows from its format.
#
# The upgrade happens *between* two on-device runs, so no single Gradle task can express this: the
# host owns the lifecycle. On N-1 the harness signs up, restores the seed, sets the backup folder
# and moves two settings off their defaults. The host force-stops and runs `adb install -r`, then
# checks it was an upgrade and not a reinstall. On N the harness checks unlock, hint, screens,
# settings and folder, and backs up. The host then compares that backup with the seed, field by
# field. Build the harness first: ./gradlew :upgrade-test:assembleDebug
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

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
    echo "usage: $0 <previous-qa.apk> <previous-seed.bak> <new-qa.apk> [output-dir]" >&2
    exit 2
fi
previous_apk="$1"
seed="$2"
new_apk="$3"
if [ ! -f settings.gradle ]; then
    echo "error: run this from the repository root." >&2
    exit 2
fi
harness_init "${4:-upgrade-test-out/upgrade}"

SEED_ON_DEVICE="upgrade-test-seed.bak"

# Everything that can fail without a device fails first: a bad input should cost nothing, not a
# wiped emulator and a minute of setup.
for apk in "$previous_apk" "$new_apk"; do
    [ -s "$apk" ] || { echo "error: $apk is missing or empty." >&2; exit 1; }
    assert_qa_apk "$apk"
done
previous_code=$(apk_version_code "$previous_apk")
new_code=$(apk_version_code "$new_apk")
echo "N-1 versionCode=$previous_code, N versionCode=$new_code"
if ! printf '%s %s' "$previous_code" "$new_code" | grep -qE '^[0-9]+ [0-9]+$'; then
    echo "error: could not read versionCodes ('$previous_code', '$new_code') with $AAPT2." >&2
    exit 1
fi
# `adb install -r` on a lower versionCode fails halfway through with INSTALL_FAILED_VERSION_DOWNGRADE.
if [ "$new_code" -le "$previous_code" ]; then
    echo "error: N ($new_code) does not supersede N-1 ($previous_code); an upgrade needs a higher versionCode." >&2
    exit 1
fi
seed_password=$(password_for_format "$(inspect_backup --header "$seed")")
if ! rows=$(expected_rows_argument "$seed" "$seed_password"); then
    echo "error: $seed does not decode with its format's password." >&2
    exit 1
fi

echo "== Install N-1 =="
clean_slate
adb install "$previous_apk" > /dev/null
first_install_before=$(installed_field firstInstallTime)
# An unreadable value would reduce the upgrade check to two empty strings, which passes vacuously.
if [ -z "$first_install_before" ]; then
    echo "error: could not read firstInstallTime after installing N-1." >&2
    exit 1
fi
grant_notifications
install_harness
push_to_downloads "$seed" "$SEED_ON_DEVICE"
reset_backup_dir

run_phase prepare UpgradeTest#prepareOnPreviousRelease \
    -e restoreFile "$SEED_ON_DEVICE" \
    -e restorePassword "$seed_password" \
    -e backupDir "$BACKUP_DIR"

echo "== Upgrade in place =="
# force-stop, never `pm clear` or uninstall: wiping /data would turn this into a fresh-install test
# that still passes. No -d: permitting a downgrade would hide a lowered versionCode.
adb shell am force-stop "$APP_PACKAGE"
adb install -r "$new_apk" > /dev/null
first_install_after=$(installed_field firstInstallTime)
installed_code=$(installed_field versionCode | awk '{print $1}')
# The single check that stops this decaying into a fresh-install test.
if [ "$first_install_after" != "$first_install_before" ]; then
    echo "error: firstInstallTime changed ($first_install_before -> $first_install_after): the app was replaced, not upgraded." >&2
    exit 1
fi
if [ "$installed_code" != "$new_code" ]; then
    echo "error: expected versionCode $new_code after the upgrade, found $installed_code." >&2
    exit 1
fi

empty_backup_dir
run_phase verify UpgradeTest#verifyAfterUpgrade \
    -e expectedRows "$rows" \
    -e backupDir "$BACKUP_DIR"
pull_single_backup "$HARNESS_OUT/after-upgrade.bak"
assert_same_records "$seed" "$seed_password" "$HARNESS_OUT/after-upgrade.bak" upgrade

echo "== Upgrade test passed =="
