#!/usr/bin/env bash
#
# Tests for scripts/lib/crash-sentinel.sh.
#
# The sentinel is only worth anything if it cannot quietly stop matching, since a sentinel that
# never fires looks exactly like a build that never crashes. It is checked here against recorded
# logcat lines rather than a live device, so it runs in seconds on any machine and in CI with no
# emulator. The Java crash below was recorded from `adb shell am crash com.andryoga.safebox.qa` on
# emulator-5554 (API 35) on 2026-09-24; the harness crash is a real one that ended a repeated-run
# stability check of the harness on 2026-09-24.
#
# Run: scripts/tests/crash-sentinel-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/crash-sentinel.sh
source "$script_dir/../lib/crash-sentinel.sh"

APP="com.andryoga.safebox.qa"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# $1 expectation: clean|crash, $2 case name, $3 recorded logcat
expect() {
    local expectation="$1" name="$2" recorded="$3"
    local file="$work_dir/$name.txt"
    printf '%s\n' "$recorded" > "$file"
    run_case "$expectation" "$name" "$file"
}

# Same, but for the cases that are about the file itself rather than its contents.
run_case() {
    local expectation="$1" name="$2" file="$3"
    total=$((total + 1))

    local output status
    output=$(assert_no_app_crash "$file" "$APP" "test" 2>&1)
    status=$?

    if [ "$expectation" = "clean" ] && [ "$status" -ne 0 ]; then
        echo "FAIL  $name: expected a clean log, the sentinel reported a crash"
        echo "      $output"
        failures=$((failures + 1))
        return
    fi
    if [ "$expectation" = "crash" ] && [ "$status" -eq 0 ]; then
        echo "FAIL  $name: THE SENTINEL MISSED A CRASH IT MUST REPORT"
        echo "      $output"
        failures=$((failures + 1))
        return
    fi
    echo "ok    $name"
}

expect crash "java_crash_recorded_from_am_crash" '09-24 11:48:08.455   571  1473 E TaskPersister: File error accessing recents directory (directory doesn'"'"'t exist?).
--------- beginning of crash
09-24 11:48:08.721 16159 16159 E AndroidRuntime: FATAL EXCEPTION: main
09-24 11:48:08.721 16159 16159 E AndroidRuntime: Process: com.andryoga.safebox.qa, PID: 16159
09-24 11:48:08.721 16159 16159 E AndroidRuntime: android.app.RemoteServiceException$CrashedByAdbException: shell-induced crash'

expect crash "java_crash_in_a_secondary_process" '09-24 11:48:08.721 16170 16170 E AndroidRuntime: FATAL EXCEPTION: main
09-24 11:48:08.721 16170 16170 E AndroidRuntime: Process: com.andryoga.safebox.qa:worker, PID: 16170'

expect crash "native_crash_tombstone_header" '09-24 11:50:00.100 16200 16200 F DEBUG   : pid: 16159, tid: 16159, name: ndroid.safebox.qa  >>> com.andryoga.safebox.qa <<<'

expect crash "anr_with_component" '09-24 11:52:00.000   571   600 E ActivityManager: ANR in com.andryoga.safebox.qa (com.andryoga.safebox.qa/com.andryoga.safebox.ui.MainActivity)'

expect crash "anr_at_end_of_line" '09-24 11:52:00.000   571   600 E ActivityManager: ANR in com.andryoga.safebox.qa'

# A dump read through a pty ends every line in CRLF; the CR must not defeat the end-of-line anchor.
expect crash "anr_at_end_of_crlf_line" "$(printf '09-24 11:52:00.000   571   600 E ActivityManager: ANR in com.andryoga.safebox.qa\r')"

# The harness's package name extends the app's, so a crash of the harness itself must not be
# blamed on the app. The instrumentation guard already fails that run, for the right reason.
expect clean "harness_crash_is_not_an_app_crash" '--------- beginning of crash
09-24 09:54:08.058  1765  1788 E AndroidRuntime: FATAL EXCEPTION: Instr: androidx.test.runner.AndroidJUnitRunner
09-24 09:54:08.058  1765  1788 E AndroidRuntime: Process: com.andryoga.safebox.upgradetest, PID: 1765'

expect clean "other_build_type_of_the_same_app" '09-24 11:48:08.721 16159 16159 E AndroidRuntime: Process: com.andryoga.safebox.qa.debug, PID: 16159
09-24 11:52:00.000   571   600 E ActivityManager: ANR in com.andryoga.safebox.qa2 (com.andryoga.safebox.qa2/.Main)'

# A normal death is not a crash: force-stop and the upgrade both kill the process on purpose.
expect clean "ordinary_process_death" '09-24 11:48:10.000   571   600 I ActivityManager: Process com.andryoga.safebox.qa (pid 16159) has died: fg  TOP
09-24 11:48:10.001   571   600 I ActivityManager: Force stopping com.andryoga.safebox.qa appid=10363 user=0: from pid 1234'

# An unreadable log must fail closed, never read as clean.
run_case crash "missing_log_fails_closed" "$work_dir/does-not-exist.txt"
: > "$work_dir/empty.txt"
run_case crash "empty_log_fails_closed" "$work_dir/empty.txt"

echo
if [ "$failures" -ne 0 ]; then
    echo "$failures of $total crash-sentinel cases FAILED"
    exit 1
fi
echo "All $total crash-sentinel cases passed"
