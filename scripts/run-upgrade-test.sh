#!/usr/bin/env bash
#
# Drives one APK-over-APK upgrade test against a connected device or emulator.
#
# The upgrade happens *between* two on-device runs, so no single Gradle task can express this: the
# host owns the lifecycle and invokes the device driver twice, once before the upgrade and once
# after. That is also why Gradle Managed Devices cannot be used here - GMD owns its emulator inside
# one task with no way to interleave an `adb install`.
#
# Usage: scripts/run-upgrade-test.sh <baseline.apk> <new.apk> [output-dir]
#
# The instrumentation APK is expected at upgrade-test/build/outputs/apk/debug/, or wherever
# UPGRADE_TEST_APK points. Build it with:
#   ./gradlew :upgrade-test:assembleDebug
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/instrumentation-guard.sh
source "$script_dir/lib/instrumentation-guard.sh"
# shellcheck source=lib/crash-sentinel.sh
source "$script_dir/lib/crash-sentinel.sh"

APP_PACKAGE="com.andryoga.safebox.qa"
TEST_PACKAGE="com.andryoga.safebox.upgradetest"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.andryoga.safebox.upgradetest.UpgradeSmokeTest"

# The golden backup Phase A restores. It has to be one the *baseline* can read, which is why it is
# the BACKUP_VERSION 2 fixture and not a newer one - see docs/testing/upgrade-testing.md section 4.
# The name is passed to the instrumentation rather than repeated in Kotlin: the host is what puts
# the file on the device, so the host owns its name.
FIXTURE_FILE="v2_pre_totp.bak"
FIXTURE_SOURCE="upgrade-test/src/main/assets/fixtures/$FIXTURE_FILE"
# The hand-captured v1.4.4.0 export that the last part of Phase D restores into the build under
# test. Owned by the host for the same reason as the fixture above.
LEGACY_FILE="v1_legacy.bak"
LEGACY_SOURCE="upgrade-test/src/main/assets/fixtures/$LEGACY_FILE"
DEVICE_DOWNLOADS="/sdcard/Download"

# The directory Phase A grants the app as its backup location. A dedicated directory is not a
# tidiness preference: Android refuses to grant a tree over the root of shared storage or over
# Download, and the picker's refusal looks like a tap that missed. Created by the host so the test
# never has to drive the picker's "create folder" flow.
BACKUP_DIR="SafeBoxUpgradeTest"
DEVICE_BACKUP_DIR="/sdcard/$BACKUP_DIR"

# What each phase writes into the harness's own app-private storage. Phase A's is what the ten-run
# determinism acceptance diffs; Phase C's is what the upgraded build showed, and the instrumentation
# itself fails unless the two match. Both are pulled into the artifacts, so a mismatch on CI can be
# read without the device. Named by OracleFiles.BASELINE and OracleFiles.UPGRADED; they have to
# agree.
ORACLE_FILE="phase-a-oracle.txt"
UPGRADED_ORACLE_FILE="phase-c-oracle.txt"
# Phase C's second capture, after it adds an authenticator, and Phase D's captures of the vault
# restored from the upgraded build's own backup and then from the v1 fixture. Named by
# OracleFiles.WITH_AUTHENTICATOR, OracleFiles.ROUND_TRIP and OracleFiles.LEGACY.
AUTHENTICATOR_ORACLE_FILE="phase-c-authenticator-oracle.txt"
ROUND_TRIP_ORACLE_FILE="phase-d-oracle.txt"
LEGACY_ORACLE_FILE="phase-d-legacy-oracle.txt"

# Phase D restores the backup the upgraded build wrote. The app writes it into the backup
# directory under a timestamped name; the host copies it into Downloads under this fixed one, so
# the restore goes through the same picker path as Phase A's and the name is owned by one side.
ROUND_TRIP_FILE="upgraded_roundtrip.bak"
# The password Phase D's backup is taken under. The instrumentation holds the same value as
# UpgradeSmokeTest.BACKUP_PASSWORD; the host needs it only to decode the file independently.
BACKUP_PASSWORD="Fixture@Backup1"

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "usage: $0 <baseline.apk> <new.apk> [output-dir]" >&2
    exit 2
fi

baseline_apk="$1"
new_apk="$2"
out_dir="${3:-upgrade-test-out}"
test_apk="${UPGRADE_TEST_APK:-upgrade-test/build/outputs/apk/debug/upgrade-test-debug.apk}"

for apk in "$baseline_apk" "$new_apk" "$test_apk"; do
    if [ ! -s "$apk" ]; then
        echo "error: $apk is missing or empty." >&2
        if [ "$apk" = "$test_apk" ]; then
            echo "       Build it: ./gradlew :upgrade-test:assembleDebug" >&2
        fi
        exit 1
    fi
done

# Checked here rather than at the push, which happens after two installs: a typo in the fixture
# name should cost nothing, not a wiped device and a minute of setup.
for source in "$FIXTURE_SOURCE" "$LEGACY_SOURCE"; do
    if [ ! -s "$source" ]; then
        echo "error: $source is missing or empty - run this from the repo root." >&2
        exit 1
    fi
done

mkdir -p "$out_dir"

# aapt2 is needed to compare versionCodes before installing anything. Its location is not on PATH
# on either CI or a developer machine, and the build-tools directory holds several versions.
resolve_aapt2() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    local newest
    newest=$(find "$sdk/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
    if [ -z "$newest" ] || [ ! -x "$newest/aapt2" ]; then
        echo "error: no aapt2 found under $sdk/build-tools." >&2
        echo "       Set ANDROID_HOME to an SDK that has build-tools installed." >&2
        exit 1
    fi
    printf '%s\n' "$newest/aapt2"
}
AAPT2=$(resolve_aapt2)

apk_version_code() {
    "$AAPT2" dump badging "$1" | sed -n "s/^package:.*versionCode='\([0-9]\{1,\}\)'.*/\1/p" | head -n 1
}

apk_package() {
    "$AAPT2" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1
}

# dumpsys pads its output and emits CRLF line endings through adb shell, so values are trimmed
# rather than used raw.
installed_field() {
    adb shell dumpsys package "$APP_PACKAGE" | tr -d '\r' |
        sed -n "s/^[[:space:]]*$1=\(.*\)$/\1/p" | head -n 1
}

# Every adb call in this script is bare `adb`, which honours ANDROID_SERIAL. The target is
# therefore resolved once and exported, rather than left to adb's "is there exactly one device
# right now" rule: a second device appearing mid-run (a phone plugged in to charge is enough)
# would otherwise break the run at whichever step happened to come next.
resolve_device() {
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        printf '%s\n' "$ANDROID_SERIAL"
        return
    fi
    local attached count
    attached=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    count=$(printf '%s' "$attached" | grep -c . || true)
    if [ "$count" -ne 1 ]; then
        echo "error: expected exactly one connected device, found $count." >&2
        echo "       Set ANDROID_SERIAL to choose one. Attached:" >&2
        adb devices -l | sed -n '2,$p' >&2
        exit 1
    fi
    printf '%s\n' "$attached"
}
ANDROID_SERIAL=$(resolve_device)
export ANDROID_SERIAL

adb wait-for-device

# This run is destructive by design: it uninstalls the QA build, then drives a scripted sign-up
# against whatever is left. That is fine on a throwaway emulator and is not fine on a handset
# somebody carries, where the QA build may hold real data - and a green result from a personal
# phone is misleading anyway, because it says nothing about the image CI actually uses.
device_characteristics=$(adb shell getprop ro.build.characteristics | tr -d '\r')
case "$device_characteristics" in
    *emulator*) ;;
    *)
        if [ "${UPGRADE_TEST_ALLOW_PHYSICAL:-0}" != "1" ]; then
            echo "error: $ANDROID_SERIAL ($(adb shell getprop ro.product.model | tr -d '\r')) is not an emulator." >&2
            echo "       This run uninstalls $APP_PACKAGE and signs up from scratch on it." >&2
            echo "       Re-run with UPGRADE_TEST_ALLOW_PHYSICAL=1 if that is genuinely intended." >&2
            exit 1
        fi
        echo "warning: running against physical device $ANDROID_SERIAL by explicit request." >&2
        ;;
esac

echo "Device: $ANDROID_SERIAL $(adb shell getprop ro.product.model | tr -d '\r') - $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"

# Logcat is collected whatever happens - a failure on a CI emulator nobody can attach to is only
# actionable if the log comes back with it. Phase C's oracle is pulled here too rather than after
# the phase, because the phase fails exactly when that file matters: it is the other half of the
# diff the failure message summarises.
collect_logcat() {
    adb logcat -d > "$out_dir/logcat.txt" 2>/dev/null || true
    for name in "$UPGRADED_ORACLE_FILE" "$AUTHENTICATOR_ORACLE_FILE" "$ROUND_TRIP_ORACLE_FILE" \
        "$LEGACY_ORACLE_FILE"; do
        pull_harness_file "$name" || true
    done
    echo "Artifacts in $out_dir/"
}

# Copies a file the instrumentation wrote to its own app-private storage into the artifacts, and
# fails if there is none. Read back with run-as rather than adb pull: that storage needs no
# permissions on the device and cannot be confused with anything the app under test wrote. The
# harness APK is debug-signed, so run-as is allowed.
#
# Existence is checked separately, first. `adb exec-out` carries the remote command's stderr on
# the same stream as its stdout, so a missing file used to arrive as a one-line "No such file or
# directory" artifact - non-empty, and therefore indistinguishable from an oracle by a size test.
pull_harness_file() {
    local name="$1"
    rm -f "$out_dir/$name"
    adb shell run-as "$TEST_PACKAGE" test -f "files/$name" || return 1
    adb exec-out run-as "$TEST_PACKAGE" cat "files/$name" > "$out_dir/$name"
    [ -s "$out_dir/$name" ]
}
trap collect_logcat EXIT

# Group 9. Dumps the two buffers crashes and ANRs are written to, and fails the run if the app under
# test appears in either. Run after every phase rather than once at the end, so that a crash is
# blamed on the build it happened in. The baseline crashing during seeding is a broken fixture, not
# an upgrade regression, and the message should say which.
scan_for_crashes() {
    local phase="$1"
    local dump="$out_dir/crash-scan-$phase.txt"
    adb logcat -d -b crash -b system > "$dump" 2>/dev/null || true
    assert_no_app_crash "$dump" "$APP_PACKAGE" "phase $phase"
}

# Handing this script a debug or release APK by mistake is the easiest way to make the whole
# exercise meaningless, and it does not fail obviously: the wrong package installs fine, and the
# driver then reports only that it cannot find a launch intent.
for apk in "$baseline_apk" "$new_apk"; do
    pkg=$(apk_package "$apk")
    if [ "$pkg" != "$APP_PACKAGE" ]; then
        echo "error: $apk declares applicationId '$pkg', expected '$APP_PACKAGE'." >&2
        echo "       Three build types mean three applicationIds, and Android refuses to upgrade" >&2
        echo "       one into another. qa -> qa is the only vehicle that exists." >&2
        exit 1
    fi
done

baseline_code=$(apk_version_code "$baseline_apk")
new_code=$(apk_version_code "$new_apk")
echo "Baseline versionCode=$baseline_code, build under test versionCode=$new_code"

# An empty value here is not a low version, it is a broken read - a truncated APK, or an aapt2 that
# printed something unexpected. Left unchecked, `[ "" -le 23 ]` aborts the script with "integer
# expression expected", which points at the comparison rather than at the APK that caused it.
for code in "$baseline_code" "$new_code"; do
    if ! printf '%s' "$code" | grep -qE '^[0-9]+$'; then
        echo "error: could not read a versionCode from the APKs ('$baseline_code', '$new_code')." >&2
        echo "       $AAPT2 dump badging produced no 'package: versionCode=' line." >&2
        exit 1
    fi
done

# Checked before installing anything, because `adb install -r` on a lower versionCode fails with
# INSTALL_FAILED_VERSION_DOWNGRADE halfway through the run and the cause is far less obvious there.
if [ "$new_code" -le "$baseline_code" ]; then
    echo "error: the build under test ($new_code) does not supersede the baseline ($baseline_code)." >&2
    echo "       An upgrade test needs a strictly higher versionCode." >&2
    exit 1
fi

echo "== Clean slate =="
adb uninstall "$APP_PACKAGE" > /dev/null 2>&1 || true
adb uninstall "$TEST_PACKAGE" > /dev/null 2>&1 || true
# The crash scans read the system buffer, which ActivityManager fills quickly on an emulator. With
# the default size, a crash early in Phase A could roll out before it is read, and the scan would
# then pass on a log that no longer contains it. Best effort: some images refuse a resize, and the
# scan still works on whatever buffer is there.
adb logcat -G 16M > /dev/null 2>&1 || true
adb logcat -c || true

echo "== Install baseline =="
adb install "$baseline_apk"
first_install_before=$(installed_field firstInstallTime)
# An unreadable value would reduce the comparison after the upgrade to two empty strings, which
# succeeds and proves nothing - the same class of vacuous pass as a zero-test run.
if [ -z "$first_install_before" ]; then
    echo "error: could not read firstInstallTime for $APP_PACKAGE after installing the baseline." >&2
    echo "       Either the install did not take, or dumpsys output has changed shape." >&2
    exit 1
fi
echo "firstInstallTime=$first_install_before"

# With a backup location set, the records screen raises a notification-permission rationale on
# every cold start until the permission is granted. It cannot be dismissed with back, and it arrives
# asynchronously after the list, so driving it would put a race into every phase that lands on
# records. Granting up front removes it deterministically; runtime grants survive `install -r`, so
# the build under test inherits it the way a user's device would. API 33 introduced the
# permission, so older images have nothing to grant.
grant_notifications() {
    if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
        adb shell pm grant "$APP_PACKAGE" android.permission.POST_NOTIFICATIONS
    fi
}
grant_notifications

echo "== Install harness =="
adb install "$test_apk"

# Phase A seeds the vault by restoring this through the real document picker, so the file has to be
# somewhere the picker can see - app-private storage is not. MediaProvider is asked to rescan
# because DocumentsUI lists the Downloads root from the media database, not from the filesystem: a
# pushed file that nobody announced is on disk but not offered.
echo "== Push fixtures =="
# A round-trip backup left by an earlier run would let Phase D restore last run's export if this
# run's copy step were ever skipped, so it goes before anything is announced to MediaProvider.
adb shell rm -f "$DEVICE_DOWNLOADS/$ROUND_TRIP_FILE"
adb push "$FIXTURE_SOURCE" "$DEVICE_DOWNLOADS/" > /dev/null
adb push "$LEGACY_SOURCE" "$DEVICE_DOWNLOADS/" > /dev/null
adb shell content call --uri content://media/external/file --method scan_volume --arg external > /dev/null
echo "$FIXTURE_FILE, $LEGACY_FILE -> $DEVICE_DOWNLOADS/"

# Removed and recreated rather than just created: a backup file left behind by an earlier run would
# still be there when the next one grants the same directory, and Phase A has to produce the same
# state every time it runs. No rescan is needed here - unlike the Downloads root, the tree picker
# lists directories from the filesystem, so this one is visible the moment it exists.
adb shell rm -rf "$DEVICE_BACKUP_DIR"
adb shell mkdir -p "$DEVICE_BACKUP_DIR"
echo "backup location -> $DEVICE_BACKUP_DIR/"

# Each phase runs exactly one test method. A comma-separated filter silently runs only the first
# class, and any filter that matches nothing exits 0 - which is what the guard below exists for.
run_phase() {
    local label="$1" method="$2" min_tests="$3"
    local output="$out_dir/instrumentation-$label.txt"

    echo "== Phase $label: $method =="
    # The exit status of am instrument is deliberately ignored: it is 0 for a failed test, 0 for a
    # filter that matched nothing, and 0 for a run that never started. The output is the only
    # trustworthy signal, so it is parsed instead.
    adb shell am instrument -w -r \
        -e class "$TEST_CLASS#$method" \
        -e fixtureFile "$FIXTURE_FILE" \
        -e backupDir "$BACKUP_DIR" \
        -e roundTripFile "$ROUND_TRIP_FILE" \
        -e legacyFile "$LEGACY_FILE" \
        "$TEST_PACKAGE/$TEST_RUNNER" 2>&1 | tee "$output" || true

    # The crash scan runs even when the phase failed. An app crash is a common *reason* for a
    # phase failing, and the phase's own message only describes the screen the crash left behind.
    local status=0
    assert_instrumentation_ran "$output" "$min_tests" || status=1
    scan_for_crashes "$label" || status=1
    return "$status"
}

run_phase seed seedVaultOnBaselineBuild 1

echo "== Capture oracle =="
if ! pull_harness_file "$ORACLE_FILE"; then
    echo "error: Phase A produced no oracle at $out_dir/$ORACLE_FILE." >&2
    echo "       The phase passed, so the capture wrote nothing or run-as was refused." >&2
    exit 1
fi
echo "$ORACLE_FILE: $(wc -l < "$out_dir/$ORACLE_FILE" | tr -d ' ') lines"

echo "== Upgrade in place =="
# force-stop, never `pm clear` and never `uninstall`: wiping /data would turn this into a
# fresh-install test that still passes, which is precisely the failure mode being guarded against.
adb shell am force-stop "$APP_PACKAGE"
# No -d flag: permitting a downgrade would hide a lowered versionCode.
adb install -r "$new_apk"

first_install_after=$(installed_field firstInstallTime)
installed_code=$(installed_field versionCode | awk '{print $1}')
echo "firstInstallTime=$first_install_after, versionCode=$installed_code"

# The single check that stops this decaying into a fresh-install test a year from now.
if [ "$first_install_after" != "$first_install_before" ]; then
    echo "error: firstInstallTime changed ($first_install_before -> $first_install_after)." >&2
    echo "       The app was replaced, not upgraded - /data did not survive." >&2
    exit 1
fi
if [ "$installed_code" != "$new_code" ]; then
    echo "error: expected versionCode $new_code after the upgrade, found $installed_code." >&2
    exit 1
fi

# Phase C adds an authenticator, and the only way into that form is through the QR scanner, which
# asks for the camera on first open. The system dialog would cover the scanner's manual-entry
# button, so it is granted here, after the upgrade: the baseline does not declare the permission,
# so there is nothing to grant before it. A failure is fatal and named, because the alternative is
# a Phase C timeout staring at a permission dialog.
if ! adb shell pm grant "$APP_PACKAGE" android.permission.CAMERA; then
    echo "error: could not grant CAMERA to the build under test." >&2
    echo "       It no longer declares it, or the grant was refused; Phase C cannot reach the" >&2
    echo "       authenticator form behind the QR scanner without it." >&2
    exit 1
fi

run_phase verify verifyVaultAfterUpgrade 1

# Phase D, plan Group 6. The first half backs the upgraded vault up through the app.
run_phase backup backUpUpgradedVault 1

# The file is decoded here, independently of the app, before `pm clear` puts the vault beyond
# recovery: if the export is broken, this says so by name instead of leaving the restore to fail
# with the app's generic message. The directory was emptied before Phase A and auto-backup is off,
# so exactly one file is expected; more means something else is writing backups.
echo "== Collect round-trip backup =="
# No mapfile: macOS still ships bash 3.2, and this script is run locally as often as on CI.
backups=$(adb shell ls "$DEVICE_BACKUP_DIR" | tr -d '\r' | grep -E '^SafeBoxBackup.*\.bak$' || true)
backup_count=$(printf '%s' "$backups" | grep -c . || true)
if [ "$backup_count" -ne 1 ]; then
    echo "error: expected one backup in $DEVICE_BACKUP_DIR after Phase D's backup, found $backup_count: ${backups:-none}." >&2
    exit 1
fi
adb exec-out cat "$DEVICE_BACKUP_DIR/$backups" > "$out_dir/$ROUND_TRIP_FILE"
if [ ! -s "$out_dir/$ROUND_TRIP_FILE" ]; then
    echo "error: $backups is empty." >&2
    exit 1
fi
echo "$backups: $(wc -c < "$out_dir/$ROUND_TRIP_FILE" | tr -d ' ') bytes"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$java_bin" "$script_dir/InspectBackup.java" "$out_dir/$ROUND_TRIP_FILE" "$BACKUP_PASSWORD" \
    > "$out_dir/round-trip-backup-inspect.txt" 2>&1; then
    echo "error: the upgraded build's backup does not decode with its own password." >&2
    echo "       See $out_dir/round-trip-backup-inspect.txt." >&2
    exit 1
fi
if ! grep -qE '^AUTHENTICATOR +: 1 record\(s\)$' "$out_dir/round-trip-backup-inspect.txt"; then
    echo "error: the upgraded build's backup does not carry the authenticator Phase C added." >&2
    echo "       See $out_dir/round-trip-backup-inspect.txt." >&2
    exit 1
fi
adb shell cp "$DEVICE_BACKUP_DIR/$backups" "$DEVICE_DOWNLOADS/$ROUND_TRIP_FILE"
adb shell content call --uri content://media/external/file --method scan_volume --arg external > /dev/null
echo "$ROUND_TRIP_FILE -> $DEVICE_DOWNLOADS/"

# The one place this script clears the app, and only after the upgrade has been fully judged:
# from here on the question is whether the export restores, not whether /data survived. Clearing
# also resets runtime permissions, so the notification grant is made again for the same reason it
# was made the first time.
echo "== Clear app data =="
adb shell pm clear "$APP_PACKAGE"
grant_notifications

run_phase restore restoreBackupIntoClearedApp 1

# The v1.4.4.0 export, restored over the round-tripped vault. No clear first: a restore replaces
# everything, and the round-tripped authenticator vanishing is part of what this phase checks.
run_phase legacy restoreLegacyBackup 1

echo "== Upgrade test passed =="
