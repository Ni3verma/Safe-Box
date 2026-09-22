#!/usr/bin/env bash
#
# Derives which previously shipped versions the upgrade test should upgrade *from*.
#
# Hardcoding tags in a workflow matrix goes stale the moment you ship, and nobody notices because
# the job keeps passing against an increasingly irrelevant baseline. The tag convention
# vMAJOR.MINOR.DBVERSION.FIX makes the interesting baselines derivable instead: the third component
# is the Room schema version, so the release list alone says which releases sit on which schema.
#
# Three rules (docs/testing/upgrade-testing.md section 7):
#   previous         newest stable release that carries a SafeBox-qa.apk
#   schema-boundary  for each Room schema older than the current one, the newest stable release
#                    carrying it - these are the releases whose upgrade crosses a migration
#   oldest           the oldest release that still has an archived SafeBox-qa.apk
#
# Usage:
#   scripts/resolve-baselines.sh                    # JSON matrix of all three, de-duplicated
#   scripts/resolve-baselines.sh --rule previous    # one tag, for a manual single-baseline run
#
# Requires an authenticated `gh`. Releases without a SafeBox-qa.apk asset are skipped
# automatically, which is what makes v1.0.0, v1.1.0 and v1.2.2.0 drop out on their own.
set -euo pipefail

REPO="${UPGRADE_TEST_REPO:-Ni3verma/Safe-Box}"
SCHEMA_DIR="app/schemas/com.andryoga.safebox.data.db.SafeBoxDatabase"
FLOOR_FILE="upgrade-test/oldest-supported.txt"

rule="all"
if [ "$#" -gt 0 ]; then
    case "$1" in
        --rule)
            rule="${2:-}"
            if [ -z "$rule" ]; then
                echo "error: --rule needs a value (previous|schema-boundary|oldest|all)" >&2
                exit 2
            fi
            ;;
        *)
            echo "usage: $0 [--rule previous|schema-boundary|oldest|all]" >&2
            exit 2
            ;;
    esac
fi

# Newest first, stable only, and only releases that actually carry the APK this test installs.
# A prerelease baseline tests a state no real user is in, since Play only ever serves stable
# builds.
#
# One REST call rather than `gh release list` followed by a `gh release view` per tag: the list
# subcommand cannot return assets (`gh release list --json assets` errors with "Unknown JSON
# field"), so the obvious spelling costs one network round-trip per release and is the slowest part
# of the job. The REST payload already carries them.
candidates=$(
    gh api "repos/$REPO/releases?per_page=100" \
        --jq '.[]
              | select(.draft == false and .prerelease == false)
              | select(any(.assets[]; .name == "SafeBox-qa.apk"))
              | .tag_name'
)

if [ -z "$candidates" ]; then
    echo "error: no stable release carries a SafeBox-qa.apk asset - cannot pick a baseline." >&2
    exit 1
fi

# An optional product decision: how far back installs are still supported. Absent by default,
# because "we no longer care about upgrades from before X" is the one thing a script cannot infer.
if [ -f "$FLOOR_FILE" ]; then
    floor=$(grep -v '^[[:space:]]*#' "$FLOOR_FILE" | tr -d '[:space:]' | head -n 1)
    if [ -n "$floor" ]; then
        candidates=$(printf '%s\n' "$candidates" | awk -v floor="$floor" '
            { lines[NR] = $0 }
            $0 == floor { found = NR }
            END { for (i = 1; i <= (found ? found : NR); i++) print lines[i] }
        ')
    fi
fi

# The schema version the build under test is on, taken from the exported Room schemas rather than
# from a tag: the tag for the build under test does not exist yet when this runs.
current_schema=$(
    find "$SCHEMA_DIR" -name '*.json' -exec basename {} .json \; | sort -n | tail -n 1
)
if [ -z "$current_schema" ]; then
    echo "error: no exported Room schemas under $SCHEMA_DIR - run this from the repo root." >&2
    exit 1
fi

# vMAJOR.MINOR.DBVERSION.FIX -> DBVERSION
schema_of() {
    printf '%s\n' "$1" | sed -n 's/^v[0-9]\{1,\}\.[0-9]\{1,\}\.\([0-9]\{1,\}\)\..*/\1/p'
}

selected=""
# Appends a tag unless it is empty. Written as a full if rather than `[ -n "$1" ] && ...` because
# the && form returns 1 for an empty argument, which under `set -e` would abort the whole script.
add() {
    if [ -n "$1" ]; then
        selected="${selected}${1}"$'\n'
    fi
}

case "$rule" in
    previous | schema-boundary | oldest | all) ;;
    *)
        echo "error: unknown rule '$rule'" >&2
        exit 2
        ;;
esac

if [ "$rule" = "previous" ] || [ "$rule" = "all" ]; then
    add "$(printf '%s\n' "$candidates" | head -n 1)"
fi

# Newest release on each schema strictly older than the current one. Upgrading from any older
# release on the same schema exercises the identical migration chain, so one per schema is enough -
# the alternative is O(n^2) jobs that all prove the same thing.
if [ "$rule" = "schema-boundary" ] || [ "$rule" = "all" ]; then
    seen=""
    while IFS= read -r tag; do
        schema=$(schema_of "$tag")
        if [ -z "$schema" ] || [ "$schema" -ge "$current_schema" ]; then
            continue
        fi
        case " $seen " in
            *" $schema "*) continue ;;
        esac
        seen="$seen $schema"
        add "$tag"
    done <<< "$candidates"
fi

if [ "$rule" = "oldest" ] || [ "$rule" = "all" ]; then
    add "$(printf '%s\n' "$candidates" | tail -n 1)"
fi

# De-duplicate while preserving the order the rules produced; the rules overlap often.
selected=$(printf '%s' "$selected" | awk 'NF && !seen[$0]++')

if [ "$rule" != "all" ]; then
    printf '%s\n' "$selected" | head -n 1
    exit 0
fi

printf '{"from_tag":['
printf '%s\n' "$selected" | awk '{ printf "%s\"%s\"", (NR > 1 ? "," : ""), $0 }'
printf ']}\n'
