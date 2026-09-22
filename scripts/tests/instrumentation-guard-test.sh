#!/usr/bin/env bash
#
# Tests for scripts/lib/instrumentation-guard.sh.
#
# The guard is the one piece of this harness that cannot be allowed to rot: if it stops detecting
# an empty run, every future upgrade test silently passes. It is checked here against recorded
# `am instrument -w -r` output rather than a live device, so it runs in seconds on any machine and
# in CI with no emulator.
#
# Run: scripts/tests/instrumentation-guard-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/instrumentation-guard.sh
source "$script_dir/../lib/instrumentation-guard.sh"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# $1 expectation: pass|fail, $2 case name, $3 min tests, $4 recorded output
expect() {
    local expectation="$1" name="$2" min_tests="$3" recorded="$4"
    local file="$work_dir/$name.txt"
    printf '%s\n' "$recorded" > "$file"
    run_case "$expectation" "$name" "$file" "$min_tests"
}

# Same, but for the cases that are about the file itself rather than its contents.
run_case() {
    local expectation="$1" name="$2" file="$3" min_tests="$4"
    total=$((total + 1))

    local output status
    output=$(assert_instrumentation_ran "$file" "$min_tests" 2>&1)
    status=$?

    if [ "$expectation" = "pass" ] && [ "$status" -ne 0 ]; then
        echo "FAIL  $name: expected the guard to accept this run, it rejected it"
        echo "      $output"
        failures=$((failures + 1))
        return
    fi
    if [ "$expectation" = "fail" ] && [ "$status" -eq 0 ]; then
        echo "FAIL  $name: THE GUARD ACCEPTED A RUN IT MUST REJECT"
        echo "      $output"
        failures=$((failures + 1))
        return
    fi
    echo "ok    $name"
}

# A normal, honest single-test run.
expect pass "one_test_passed" 1 'INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: class=com.andryoga.safebox.upgradetest.UpgradeSmokeTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: test=unlockScreenAppearsAfterUpgrade
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=

Time: 12.317

OK (1 test)

INSTRUMENTATION_CODE: -1'

# The whole reason this guard exists: a class or method filter that matches nothing. am instrument
# prints OK and exits 0, and the previous attempt at this harness reported it as a green run.
expect fail "zero_tests_reported_as_ok" 1 'INSTRUMENTATION_RESULT: stream=

Time: 0

OK (0 tests)

INSTRUMENTATION_CODE: -1'

# The other shape the same mistake takes: the runner rejects the filter outright.
expect fail "no_tests_remain_exception" 1 'INSTRUMENTATION_STATUS: stream=
androidx.test.internal.runner.TestRequestBuilder$NoTestsRemainException: No tests found in com.andryoga.safebox.upgradetest.UpgradeSmokeTest
INSTRUMENTATION_STATUS_CODE: -1
INSTRUMENTATION_CODE: -1'

# Fewer tests than asked for. Observed with a comma-separated class filter, where only the first
# class runs and the omission is never mentioned.
expect fail "fewer_tests_than_required" 2 'INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_RESULT: stream=

Time: 4.1

OK (1 test)

INSTRUMENTATION_CODE: -1'

expect fail "test_failed" 1 'INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_RESULT: stream=

Time: 9.02
There was 1 failure:
1) unlockScreenAppearsAfterUpgrade(com.andryoga.safebox.upgradetest.UpgradeSmokeTest)
java.lang.IllegalStateException: could not find TEXT within 15000ms

FAILURES!!!
Tests run: 1,  Failures: 1

INSTRUMENTATION_CODE: -1'

expect fail "process_crashed" 1 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.
INSTRUMENTATION_CODE: 0'

# A missing test APK. The count would otherwise be unobtainable and the run would look empty
# rather than broken, so the guard names the real cause.
expect fail "instrumentation_component_missing" 1 'INSTRUMENTATION_FAILED: com.andryoga.safebox.upgradetest/androidx.test.runner.AndroidJUnitRunner
android.util.AndroidException: INSTRUMENTATION_FAILED'

# Output that says nothing at all about tests. Without the explicit guard this parses to an empty
# count, and an unquoted empty count in an arithmetic comparison is a bash syntax error rather
# than a failure - which would abort the script with a misleading message.
expect fail "unparseable_output" 1 'Broadcast completed: result=0'

printf '' > "$work_dir/empty.txt"
run_case fail "empty_output_file" "$work_dir/empty.txt" 1
run_case fail "missing_output_file" "$work_dir/does-not-exist.txt" 1

echo
if [ "$failures" -ne 0 ]; then
    echo "$failures of $total guard test(s) FAILED"
    exit 1
fi
echo "all $total guard tests passed"
