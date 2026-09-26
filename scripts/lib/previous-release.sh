#!/usr/bin/env bash
#
# Picks the release the upgrade test upgrades *from* (N-1) and finds the backup it restores first.
#
# One rule (docs/testing/upgrade-testing.md, "Category 1"): N-1 is the newest stable release older
# than the build under test that carries a SafeBox-qa.apk. Prereleases are never N-1 - Play only
# serves stable builds, so no real user upgrades from an RC. The seed is whatever
# upgrade-test/seed/seed.bak was at N-1's tag, i.e. a backup in exactly the format N-1 writes.
#
# The two functions are split from the network call in scripts/resolve-previous-release.sh so
# scripts/tests/previous-release-test.sh can exercise them offline. Sourced; defines functions only.
#
# macOS ships bash 3.2 and BSD tools: no mapfile, and `sort -V` is the only version sort relied on.

SEED_PATH="upgrade-test/seed/seed.bak"

# Prints N-1 for a build under test, reading the stable release tags that carry a QA APK on stdin
# (any order). Anything that is not a plain vMAJOR.MINOR.DBVERSION.FIX tag is ignored, so an RC
# or a stray tag in the input can never be picked.
#
# $1 - the tag under test (v2.2.6.0 or v2.2.6.0-rc1), or empty for a build that is not tagged yet,
#      in which case the newest candidate is N-1
# Returns 1, with a message on stderr, if no candidate is older.
previous_release() {
    local under_test="$1"
    local base="${under_test%%-*}"
    local candidates
    candidates=$(grep -E '^v[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' || true)
    local previous=""
    if [ -z "$base" ]; then
        previous=$(printf '%s\n' "$candidates" | sed '/^$/d' | sort -V | tail -n 1)
    else
        local tag
        # Strictly older: the stable tag being released is itself a candidate once its release
        # exists (release.yml publishes before a re-run), and v2.2.6.0-rc1's base is v2.2.6.0.
        while IFS= read -r tag; do
            if [ -n "$tag" ] && [ "$tag" != "$base" ] &&
                [ "$(printf '%s\n%s\n' "$tag" "$base" | sort -V | head -n 1)" = "$tag" ]; then
                previous="$tag"
            fi
        done <<< "$(printf '%s\n' "$candidates" | sed '/^$/d' | sort -V)"
    fi
    if [ -z "$previous" ]; then
        echo "error: no stable release with a SafeBox-qa.apk is older than '${under_test:-HEAD}'." >&2
        return 1
    fi
    printf '%s\n' "$previous"
}

# Writes N-1's seed backup to a file.
#
# Releases cut before the seed existed have no upgrade-test/seed/ at their tag. Each such release
# that can still be N-1 gets a row below naming the committed fixture in its format; the archive
# under upgrade-test/fixtures/ never changes, so the mapping cannot go stale. There is deliberately
# no "nearest older seed" guess: restoring a newer format than N-1 writes is not an upgrade path.
#
# $1 - N-1's tag (needs `git fetch --tags` on a shallow CI checkout)
# $2 - destination file
# Returns 1, with a message on stderr, if the tag has neither a seed nor a row.
previous_seed() {
    local tag="$1" dest="$2"
    if git cat-file -e "$tag:$SEED_PATH" 2>/dev/null; then
        git show "$tag:$SEED_PATH" > "$dest"
        return 0
    fi
    local fixture
    case "$tag" in
        # Both predate the seed and write format 2. v2.1.4.0 is the v2.1.4.0-rc3 commit, which is
        # where format-2.bak was captured.
        v2.0.4.0 | v2.1.4.0) fixture="upgrade-test/fixtures/format-2.bak" ;;
        *)
            echo "error: $tag has no $SEED_PATH and no fallback row in scripts/lib/previous-release.sh." >&2
            echo "       Add a row naming the upgrade-test/fixtures/format-<k>.bak that $tag writes." >&2
            return 1
            ;;
    esac
    cp "$fixture" "$dest"
}
