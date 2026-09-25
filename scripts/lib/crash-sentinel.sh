#!/usr/bin/env bash
#
# Crash sentinel for the app under test (Group 9 of docs/testing/upgrade-testing.md).
#
# A crash or ANR in the app does not fail the instrumentation. The instrumentation runs in its own
# process, and UI Automator only sees the screen the crash leaves behind. A harness that relaunches
# the app, which this one does when a screen does not appear, can therefore walk straight past a
# crash and still report green. This sentinel reads the device log instead and fails the run if the
# app under test crashed at any point since the log was cleared.
#
# This file is meant to be sourced. It can also be run directly for a one-off check:
#   scripts/lib/crash-sentinel.sh <logcat-dump> <package>

# Prints every log line that records the given package crashing, natively crashing, or hanging.
#
# Three signatures, each anchored on the exact package name (optionally followed by a
# ":process" suffix), so that the harness's own package - whose name extends the app's - is never
# mistaken for it:
#   - Java crash:   "AndroidRuntime: Process: <pkg>, PID: <n>"  (the line under FATAL EXCEPTION)
#   - native crash: ">>> <pkg> <<<"                               (the tombstone header from debuggerd)
#   - ANR:          "ANR in <pkg>"                                (ActivityManager)
#
# $1 - a logcat dump covering at least the crash and system buffers
# $2 - the application id to look for
app_crash_lines() {
    local logcat_file="$1"
    local package="$2"
    local escaped
    escaped=$(printf '%s' "$package" | sed 's/\./\\./g')

    grep -E \
        -e "AndroidRuntime: Process: ${escaped}(:[^,]*)?, PID" \
        -e ">>> ${escaped}(:[^ ]*)? <<<" \
        -e "ANR in ${escaped}(:[^ ]*)?( |\$)" \
        "$logcat_file" || true
}

# Fails if the app under test crashed or hung.
#
# $1 - a logcat dump covering at least the crash and system buffers
# $2 - the application id to look for
# $3 - a label for the phase the dump was taken after, used only in the message
#
# Returns 0 when the log is present and clean; prints the evidence to stderr and returns 1 otherwise.
assert_no_app_crash() {
    local logcat_file="$1"
    local package="$2"
    local phase="${3:-run}"
    local found

    # An empty or missing dump fails closed. A logcat that could not be read proves nothing about
    # crashes, and treating it as clean is the same vacuous pass as a zero-test run.
    if [ ! -s "$logcat_file" ]; then
        echo "FATAL: no logcat to scan for crashes after $phase ($logcat_file)." >&2
        echo "       An unreadable log is not a clean one." >&2
        return 1
    fi

    found=$(app_crash_lines "$logcat_file" "$package")
    if [ -n "$found" ]; then
        echo "FATAL: $package crashed or stopped responding during $phase:" >&2
        printf '%s\n' "$found" | sed 's/^/       /' >&2
        echo "       The full stack is in $logcat_file." >&2
        return 1
    fi

    echo "OK: no crash or ANR from $package during $phase."
}

# Only runs the assertion when executed directly, so sourcing stays free of side effects.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
        echo "usage: $0 <logcat-dump> <package> [phase]" >&2
        exit 2
    fi
    assert_no_app_crash "$@"
fi
