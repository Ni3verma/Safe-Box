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
DEVICE_DOWNLOADS="/sdcard/Download"

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
if [ ! -s "$FIXTURE_SOURCE" ]; then
    echo "error: $FIXTURE_SOURCE is missing or empty - run this from the repo root." >&2
    exit 1
fi

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
# actionable if the log comes back with it.
collect_logcat() {
    adb logcat -d > "$out_dir/logcat.txt" 2>/dev/null || true
    echo "Artifacts in $out_dir/"
}
trap collect_logcat EXIT

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

echo "== Install harness =="
adb install "$test_apk"

# Phase A seeds the vault by restoring this through the real document picker, so the file has to be
# somewhere the picker can see - app-private storage is not. MediaProvider is asked to rescan
# because DocumentsUI lists the Downloads root from the media database, not from the filesystem: a
# pushed file that nobody announced is on disk but not offered.
echo "== Push fixtures =="
adb push "$FIXTURE_SOURCE" "$DEVICE_DOWNLOADS/" > /dev/null
adb shell content call --uri content://media/external/file --method scan_volume --arg external > /dev/null
echo "$FIXTURE_FILE -> $DEVICE_DOWNLOADS/"

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
        "$TEST_PACKAGE/$TEST_RUNNER" 2>&1 | tee "$output" || true
    assert_instrumentation_ran "$output" "$min_tests"
}

run_phase seed seedVaultOnBaselineBuild 1

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

run_phase verify unlockScreenAppearsAfterUpgrade 1

echo "== Upgrade test passed =="
