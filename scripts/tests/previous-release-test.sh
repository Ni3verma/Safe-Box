#!/usr/bin/env bash
#
# Tests for scripts/lib/previous-release.sh.
#
# The rule runs only on release tags, far too rarely to find out there that it picks the wrong
# N-1. These cases run offline on every PR. The seed cases use the real repository tags, so they
# need `git fetch --tags` on a shallow checkout (ci.yml fetches them for this step).
#
# Run: scripts/tests/previous-release-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
# shellcheck source=../lib/previous-release.sh
source "$script_dir/../lib/previous-release.sh"
cd "$repo_root" || exit 1

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# Releases as the API returns them: newest first by date, not by version, with an old hotfix
# published after a newer minor to prove the order comes from the version and not the list.
CANDIDATES="v2.0.4.0
v1.7.4.2
v2.2.6.0
v1.3.3.0
v2.1.6.0
not-a-release
v2.1.6.0-rc1"

# $1 case name, $2 tag under test, $3 expected N-1 (or "error")
expect_previous() {
    local name="$1" under_test="$2" expected="$3"
    total=$((total + 1))
    local actual status
    actual=$(printf '%s\n' "$CANDIDATES" | previous_release "$under_test" 2>/dev/null)
    status=$?
    if [ "$expected" = "error" ]; then
        if [ "$status" -eq 0 ]; then
            echo "FAIL  $name: expected an error, got '$actual'"
            failures=$((failures + 1))
        fi
    elif [ "$status" -ne 0 ] || [ "$actual" != "$expected" ]; then
        echo "FAIL  $name: expected '$expected', got '$actual' (exit $status)"
        failures=$((failures + 1))
    fi
}

expect_previous "an RC upgrades from the newest older stable release" "v2.3.6.0-rc1" "v2.2.6.0"
expect_previous "a stable tag excludes itself" "v2.2.6.0" "v2.1.6.0"
expect_previous "an RC of an already-released version excludes it" "v2.2.6.0-rc2" "v2.1.6.0"
expect_previous "a hotfix upgrades from the release it fixes" "v2.2.6.1" "v2.2.6.0"
expect_previous "versions compare numerically, not as text" "v2.10.6.0" "v2.2.6.0"
expect_previous "an untagged build upgrades from the newest release" "" "v2.2.6.0"
expect_previous "RC and malformed candidates are never N-1" "v2.1.6.0" "v2.0.4.0"
expect_previous "nothing older is an error" "v1.3.3.0" "error"

total=$((total + 1))
if printf '' | previous_release "v2.0.4.0" > /dev/null 2>&1; then
    echo "FAIL  no candidates at all: expected an error"
    failures=$((failures + 1))
fi

# $1 case name, $2 tag, $3 expected file whose bytes the seed must equal (or "error")
expect_seed() {
    local name="$1" tag="$2" expected="$3"
    total=$((total + 1))
    local out="$work_dir/seed-$total.bak"
    if ! previous_seed "$tag" "$out" 2>/dev/null; then
        if [ "$expected" != "error" ]; then
            echo "FAIL  $name: previous_seed failed"
            failures=$((failures + 1))
        fi
        return
    fi
    if [ "$expected" = "error" ] || ! cmp -s "$out" "$expected"; then
        echo "FAIL  $name: seed does not match '$expected'"
        failures=$((failures + 1))
    fi
}

if git rev-parse -q --verify "refs/tags/v2.0.4.0" > /dev/null; then
    expect_seed "v2.0.4.0 predates the seed and falls back to format-2" "v2.0.4.0" \
        "upgrade-test/fixtures/format-2.bak"
    expect_seed "a release with neither a seed nor a row is an error" "v1.7.4.2" "error"
else
    echo "FAIL  release tags are missing locally; run: git fetch --tags"
    failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
    echo "$failures of $total cases failed."
    exit 1
fi
echo "All $total cases passed."
