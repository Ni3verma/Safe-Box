#!/usr/bin/env bash
#
# Guard against a silently empty instrumentation run.
#
# `am instrument` exits 0 when the class filter matches nothing, when every test is ignored, and
# even when tests fail. The Gradle equivalent behaves the same way (see the build-and-test skill).
# A harness that trusts the exit code therefore reports a green upgrade test that executed zero
# assertions, which is worse than no test at all because it is believed.
#
# This file is meant to be sourced. It can also be run directly for a one-off check:
#   scripts/lib/instrumentation-guard.sh <output-file> <min-tests>

# Extracts the number of tests JUnit reported as executed.
#
# Reads, in order of authority:
#   1. "OK (N tests)"        - the success summary
#   2. "Tests run: N, ..."   - the failure summary
#   3. "numtests=N"          - per-test status lines, used only when neither summary is present,
#                              which happens when the run is cut short mid-way
# Prints the count, or nothing at all if the output contains no evidence of any test at all.
instrumentation_test_count() {
    local output_file="$1"
    local count

    count=$(sed -n 's/^OK (\([0-9]\{1,\}\) test.*/\1/p' "$output_file" | tail -n 1)
    if [ -n "$count" ]; then
        printf '%s\n' "$count"
        return 0
    fi

    count=$(sed -n 's/^Tests run: \([0-9]\{1,\}\).*/\1/p' "$output_file" | tail -n 1)
    if [ -n "$count" ]; then
        printf '%s\n' "$count"
        return 0
    fi

    # sort -n over every reported numtests: the value is the size of the whole run, so the largest
    # one seen is the count the runner intended to execute.
    count=$(sed -n 's/.*numtests=\([0-9]\{1,\}\).*/\1/p' "$output_file" | sort -n | tail -n 1)
    if [ -n "$count" ]; then
        printf '%s\n' "$count"
    fi
}

# Fails unless the instrumentation run executed at least <min-tests> tests and all of them passed.
#
# $1 - file holding the combined output of `am instrument -w -r`
# $2 - minimum number of tests that must have executed (must be >= 1)
#
# Returns 0 only when the run is trustworthy; prints the reason to stderr and returns 1 otherwise.
assert_instrumentation_ran() {
    local output_file="$1"
    local min_tests="$2"
    local count

    if [ ! -s "$output_file" ]; then
        echo "FATAL: instrumentation produced no output at all ($output_file)." >&2
        echo "       The run did not start. Treat this as a failure, never as zero tests." >&2
        return 1
    fi

    # Startup failures never reach the JUnit summary, so they are matched before counting.
    # INSTRUMENTATION_FAILED means the component does not exist, usually a stale or missing test
    # APK; shortMsg/Process crashed means the test process died and any partial count is a lie.
    if grep -q '^INSTRUMENTATION_FAILED' "$output_file"; then
        echo "FATAL: instrumentation component could not be started." >&2
        echo "       Is the test APK installed, and does the runner name match the manifest?" >&2
        return 1
    fi
    if grep -qE 'Process crashed|^INSTRUMENTATION_ABORTED|shortMsg=' "$output_file"; then
        echo "FATAL: the instrumentation process crashed; results are not trustworthy." >&2
        return 1
    fi

    count=$(instrumentation_test_count "$output_file")
    if [ -z "$count" ]; then
        echo "FATAL: could not determine how many tests ran from $output_file." >&2
        echo "       No 'OK (n tests)', no 'Tests run:' and no 'numtests='. Assume zero." >&2
        return 1
    fi

    if [ "$count" -lt "$min_tests" ]; then
        echo "FATAL: $count test(s) executed, expected at least $min_tests." >&2
        echo "       A filter that matches nothing exits 0 and reports OK - check the -e class" >&2
        echo "       argument against the method names in UpgradeSmokeTest." >&2
        return 1
    fi

    # Checked after the count so that a run which both failed and executed nothing reports the
    # emptiness, which is the more misleading of the two.
    if grep -q '^FAILURES!!!' "$output_file"; then
        echo "FATAL: $count test(s) ran but at least one failed." >&2
        return 1
    fi

    echo "OK: $count test(s) executed and passed (minimum $min_tests)."
}

# Only runs the assertion when executed directly, so sourcing stays free of side effects.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    if [ "$#" -ne 2 ]; then
        echo "usage: $0 <instrumentation-output-file> <min-tests>" >&2
        exit 2
    fi
    assert_instrumentation_ran "$1" "$2"
fi
