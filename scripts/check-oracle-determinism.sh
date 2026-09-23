#!/usr/bin/env bash
#
# Runs the upgrade test repeatedly and proves every run describes the same vault.
#
# Phase A's acceptance is not "it passed once". A seeding harness that is only mostly
# deterministic produces an oracle that drifts, and a later stage diffing a pre-upgrade oracle
# against a post-upgrade one then reports differences the upgrade did not cause. Those are the
# failures that get explained away, and explaining away failures is how the previous attempt at
# this harness became worthless. So the oracle is pinned here, before anything is allowed to
# depend on it: N consecutive runs, byte-identical output, or the acceptance has not been met.
#
# A diff is not automatically the harness's fault - a value that moves on its own, such as a
# timestamp the oracle should never have read, shows up exactly the same way. Either way the
# oracle is not yet something to build on, which is the point.
#
# Usage: scripts/check-oracle-determinism.sh <baseline.apk> <new.apk> [runs] [output-dir]
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

ORACLE_FILE="phase-a-oracle.txt"
DEFAULT_RUNS=10

if [ "$#" -lt 2 ] || [ "$#" -gt 4 ]; then
    echo "usage: $0 <baseline.apk> <new.apk> [runs] [output-dir]" >&2
    exit 2
fi

baseline_apk="$1"
new_apk="$2"
runs="${3:-$DEFAULT_RUNS}"
out_dir="${4:-upgrade-test-out/determinism}"

# A determinism check that compares nothing must not report success. With runs=0 `seq` emits
# nothing and the loop never executes; with a non-numeric value `seq` fails inside the for-list,
# which `set -e` does not catch; with runs=1 there is a reference and nothing to compare it to.
# All three used to print "produced a byte-identical oracle" and exit 0, which is precisely the
# silent pass that scripts/lib/instrumentation-guard.sh exists to prevent elsewhere.
case "$runs" in
    ''|*[!0-9]*)
        echo "error: runs must be a whole number of 2 or more, got '$runs'." >&2
        exit 2
        ;;
esac
if [ "$runs" -lt 2 ]; then
    echo "error: runs must be 2 or more to compare anything, got '$runs'." >&2
    exit 2
fi

mkdir -p "$out_dir/oracles"
reference=""

for run in $(seq 1 "$runs"); do
    label=$(printf "run-%02d" "$run")
    echo "== Determinism $label of $runs =="
    if ! "$script_dir/run-upgrade-test.sh" "$baseline_apk" "$new_apk" "$out_dir/$label"; then
        echo "FATAL: $label failed. The oracle cannot be compared across runs that do not pass." >&2
        exit 1
    fi

    oracle="$out_dir/oracles/$label.txt"
    cp "$out_dir/$label/$ORACLE_FILE" "$oracle"

    if [ -z "$reference" ]; then
        reference="$oracle"
        echo "$label: reference oracle, $(wc -l < "$oracle" | tr -d ' ') lines"
        continue
    fi

    if ! diff -u "$reference" "$oracle"; then
        echo "FATAL: $label describes a different vault from run-01 (diff above)." >&2
        exit 1
    fi
    echo "$label: identical to run-01"
done

echo "== $runs runs produced a byte-identical oracle =="
