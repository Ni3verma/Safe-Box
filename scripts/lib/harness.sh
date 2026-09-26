#!/usr/bin/env bash
#
# Device plumbing shared by scripts/run-upgrade-test.sh and scripts/run-restore-test.sh.
#
# Both runners install the QA build, drive the on-device harness (:upgrade-test) one test method
# per `am instrument` call, pull the backup each phase takes, and compare it with the file that
# was restored. Everything they share lives here so the two cannot drift. Source it after
# scripts/lib/backup-files.sh, crash-sentinel.sh and instrumentation-guard.sh; it defines
# variables and functions only, until harness_init is called.
#
# macOS still ships bash 3.2, and these run locally as often as on CI: no mapfile, no associative
# arrays.

APP_PACKAGE="com.andryoga.safebox.qa"
TEST_PACKAGE="com.andryoga.safebox.upgradetest"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
DEVICE_DOWNLOADS="/sdcard/Download"

# SHA-256 of the certificate every QA APK is signed with (the nonProd keystore), read with
# `apksigner verify --print-certs` from v1.4.4.0's and v2.0.4.0's release assets on 2026-09-26.
# `adb install -r` refuses a different signer anyway, but as INSTALL_FAILED_UPDATE_INCOMPATIBLE
# half-way through a run; checking first names the APK that is wrong. Changes only if the nonProd
# keystore is rotated, which would also break every real upgrade of an installed QA build.
QA_CERT_SHA256="257ab2043588f0b355bba6a9c9f199c088f079f6306536cd4c94fc2eba7b113d"

# The directory the app is granted as its backup location. Dedicated because Android refuses to
# grant a tree over the root of shared storage or over Download, and the picker's refusal looks like
# a tap that missed. Created by the host so the test never drives the picker's "create folder" flow.
BACKUP_DIR="SafeBoxUpgradeTest"
DEVICE_BACKUP_DIR="/sdcard/$BACKUP_DIR"

# Validates the harness APK, picks the device, refuses anything but an emulator, and arranges for
# logcat to be collected on exit. Sets HARNESS_OUT, TEST_APK and AAPT2, exports ANDROID_SERIAL.
#
# $1 - output directory for logs, instrumentation output and pulled backups
harness_init() {
    HARNESS_OUT="$1"
    TEST_APK="${UPGRADE_TEST_APK:-upgrade-test/build/outputs/apk/debug/upgrade-test-debug.apk}"
    if [ ! -s "$TEST_APK" ]; then
        echo "error: $TEST_APK is missing. Build it: ./gradlew :upgrade-test:assembleDebug" >&2
        exit 1
    fi
    mkdir -p "$HARNESS_OUT"
    AAPT2=$(resolve_build_tool aapt2)
    APKSIGNER=$(resolve_build_tool apksigner)

    ANDROID_SERIAL=$(resolve_device)
    export ANDROID_SERIAL
    adb wait-for-device

    # Destructive by design: the run uninstalls the QA build and signs up from scratch. Fine on a
    # throwaway emulator, not on a handset somebody carries, where the QA build may hold real data.
    local characteristics
    characteristics=$(adb shell getprop ro.build.characteristics | tr -d '\r')
    case "$characteristics" in
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
    echo "Device: $ANDROID_SERIAL $(adb shell getprop ro.product.model | tr -d '\r') - API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"

    trap 'adb logcat -d > "$HARNESS_OUT/logcat.txt" 2>/dev/null || true; echo "Artifacts in $HARNESS_OUT/"' EXIT
}

# aapt2 and apksigner are not on PATH on CI or a developer machine, and build-tools holds several
# versions; the newest is taken.
#
# $1 - the tool's file name inside build-tools/<version>/
resolve_build_tool() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    local newest
    newest=$(find "$sdk/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
    if [ -z "$newest" ] || [ ! -x "$newest/$1" ]; then
        echo "error: no $1 found under $sdk/build-tools; set ANDROID_HOME." >&2
        exit 1
    fi
    printf '%s\n' "$newest/$1"
}

# Every adb call is bare `adb`, which honours ANDROID_SERIAL, so the target is resolved once:
# a second device appearing mid-run (a phone plugged in to charge) would otherwise break whichever
# step came next.
resolve_device() {
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        printf '%s\n' "$ANDROID_SERIAL"
        return
    fi
    local attached count
    attached=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    count=$(printf '%s' "$attached" | grep -c . || true)
    if [ "$count" -ne 1 ]; then
        echo "error: expected exactly one connected device, found $count; set ANDROID_SERIAL." >&2
        adb devices -l | sed -n '2,$p' >&2
        exit 1
    fi
    printf '%s\n' "$attached"
}

apk_version_code() {
    "$AAPT2" dump badging "$1" | sed -n "s/^package:.*versionCode='\([0-9]\{1,\}\)'.*/\1/p" | head -n 1
}

# Handing a runner a debug or release APK by mistake installs fine and then fails far from the
# cause, with no launch intent for the QA package. Three build types mean three applicationIds.
# The signer is checked too: a QA APK built with a different keystore (a fork, a rotated key)
# would fail the upgrade install with a message that points nowhere near the cause.
#
# $1 - the APK
assert_qa_apk() {
    local pkg
    pkg=$("$AAPT2" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1) || true
    if [ "$pkg" != "$APP_PACKAGE" ]; then
        echo "error: $1 declares applicationId '$pkg', expected '$APP_PACKAGE' (a QA APK)." >&2
        exit 1
    fi
    local cert
    # The line prefix differs by build-tools version ("Signer #1 certificate ..." in 36.x,
    # "V2 Signer: certificate ..." in 37), so only the digest is matched. De-duplicated, because 37
    # prints one line per signature scheme; more than one distinct digest fails the comparison.
    # `|| true`: apksigner exits non-zero on an unsigned APK, and under pipefail the assignment
    # would then abort the runner silently instead of reaching the message below.
    cert=$("$APKSIGNER" verify --print-certs "$1" 2>/dev/null |
        sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\).*/\1/p' | sort -u | tr '\n' ' ' |
        sed 's/ $//') || true
    if [ "$cert" != "$QA_CERT_SHA256" ]; then
        echo "error: $1 is signed with certificate '${cert:-unreadable}'," >&2
        echo "       expected the QA certificate $QA_CERT_SHA256." >&2
        exit 1
    fi
}

# dumpsys pads its output and emits CRLF through adb shell, so values are trimmed.
installed_field() {
    adb shell dumpsys package "$APP_PACKAGE" | tr -d '\r' |
        sed -n "s/^[[:space:]]*$1=\(.*\)$/\1/p" | head -n 1
}

# Uninstalls both packages, turns animations off, and gives logcat room. The crash scans read the
# system buffer, which fills quickly on an emulator; with the default size a crash early in a run
# could roll out before it is read. Animations off removes transition waits and matches what the
# CI emulator action does, so local and CI runs time the same.
clean_slate() {
    adb uninstall "$APP_PACKAGE" > /dev/null 2>&1 || true
    adb uninstall "$TEST_PACKAGE" > /dev/null 2>&1 || true
    local scale
    for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
        adb shell settings put global "$scale" 0
    done
    adb logcat -G 16M > /dev/null 2>&1 || true
    adb logcat -c || true
}

# With a backup location set, the records screen raises a notification-permission rationale on
# every cold start until the permission is granted, asynchronously after the list. Granting up
# front removes that race; runtime grants survive `install -r`. API 33 introduced the permission.
grant_notifications() {
    if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
        adb shell pm grant "$APP_PACKAGE" android.permission.POST_NOTIFICATIONS
    fi
}

install_harness() {
    adb install "$TEST_APK" > /dev/null
}

# Puts a file where the document picker can see it, at the top of the list. DocumentsUI lists
# Downloads from the media database, not the filesystem, so MediaProvider is asked to rescan: a
# pushed file nobody announced is on disk but not offered. The file is touched first because
# `adb push` keeps the host file's mtime, and both the Recent and Downloads roots sort newest first:
# a fixture committed days ago landed below the fold of Downloads and out of Recent entirely
# (API 35 emulator, 2026-09-26), and the picker helper does not scroll.
#
# $1 - local file, $2 - its name in Downloads
push_to_downloads() {
    adb push "$1" "$DEVICE_DOWNLOADS/$2" > /dev/null
    adb shell touch "$DEVICE_DOWNLOADS/$2"
    adb shell content call --uri content://media/external/file --method scan_volume --arg external > /dev/null
}

# Recreates the backup directory. A file left by an earlier run would otherwise be pulled as this
# run's backup. The tree picker lists directories from the filesystem, so no rescan is needed.
reset_backup_dir() {
    adb shell rm -rf "$DEVICE_BACKUP_DIR"
    adb shell mkdir -p "$DEVICE_BACKUP_DIR"
}

# Empties the backup directory without removing it, so the app's persisted grant over it stays
# valid. Called before every phase that backs up, so the phase leaves exactly one file.
empty_backup_dir() {
    adb shell "rm -f $DEVICE_BACKUP_DIR/*"
}

# Runs one test method and fails unless it really ran and passed and the app did not crash.
#
# The app is force-stopped first, so every phase starts from a cold launch that lands on unlock (or
# sign-up), whatever the previous phase left on screen. The exit status of am instrument is
# ignored: it is 0 for a failed test, for a filter that matched nothing, and for a run that never
# started. The output is parsed instead (instrumentation-guard.sh). The crash scan runs even when
# the phase failed, because a crash is a common reason for a failure and the phase's own message
# only describes the screen the crash left behind.
#
# $1 - label for output files, $2 - Class#method (relative to TEST_PACKAGE), rest - extra
# `-e name value` pairs
run_phase() {
    local label="$1" test="$2"
    shift 2
    local output="$HARNESS_OUT/instrumentation-$label.txt"
    local started=$SECONDS
    echo "== $label: $test =="
    adb shell am force-stop "$APP_PACKAGE"
    adb shell am instrument -w -r -e class "$TEST_PACKAGE.$test" "$@" \
        "$TEST_PACKAGE/$TEST_RUNNER" > "$output" 2>&1 || true

    local status=0
    assert_instrumentation_ran "$output" 1 || status=1
    local dump="$HARNESS_OUT/crash-scan-$label.txt"
    adb logcat -d -b crash -b system > "$dump" 2>/dev/null || true
    assert_no_app_crash "$dump" "$APP_PACKAGE" "$label" || status=1
    echo "   $((SECONDS - started)) s"
    if [ "$status" -ne 0 ]; then
        echo "error: phase '$label' failed; see $output" >&2
        exit 1
    fi
}

# Pulls the one backup the last phase wrote. Auto-backup is off and the directory was emptied
# before the phase, so more than one file means something else is writing backups.
#
# $1 - local destination
pull_single_backup() {
    local backups count
    backups=$(adb shell ls "$DEVICE_BACKUP_DIR" | tr -d '\r' | grep -E '^SafeBoxBackup.*\.bak$' || true)
    count=$(printf '%s' "$backups" | grep -c . || true)
    if [ "$count" -ne 1 ]; then
        echo "error: expected one backup in $DEVICE_BACKUP_DIR, found $count: ${backups:-none}." >&2
        exit 1
    fi
    adb exec-out cat "$DEVICE_BACKUP_DIR/$backups" > "$1"
    if [ ! -s "$1" ]; then
        echo "error: $backups pulled empty." >&2
        exit 1
    fi
}

# The rows the records list must show for a backup, as the instrumentation's expectedRows
# argument: base64, because `am instrument -e` cannot safely carry tabs or non-ASCII titles.
#
# $1 - the .bak, $2 - its password
expected_rows_argument() {
    inspect_backup --rows "$1" "$2" | base64 | tr -d '\n'
}

# Fails, with the diff saved, unless two backups hold the same records field for field. This is
# the data check of both categories; see "Why decode instead of comparing bytes" in
# docs/testing/upgrade-testing.md.
#
# $1 - the file that was restored, $2 - its password, $3 - the backup taken afterwards,
# $4 - a label for output files
assert_same_records() {
    local expected="$HARNESS_OUT/$4-expected.txt" actual="$HARNESS_OUT/$4-actual.txt"
    inspect_backup --canonical "$1" "$2" > "$expected"
    inspect_backup --canonical "$3" "$FIXED_BACKUP_PASSWORD" > "$actual"
    if ! diff -u "$expected" "$actual" > "$HARNESS_OUT/$4.diff"; then
        echo "error: $4: the backup taken after the restore differs from the file restored." >&2
        echo "       '-' lines were in the restored file, '+' lines in the backup:" >&2
        head -n 40 "$HARNESS_OUT/$4.diff" >&2
        exit 1
    fi
    echo "   records match ($(wc -l < "$expected" | tr -d ' ') fields)"
}
