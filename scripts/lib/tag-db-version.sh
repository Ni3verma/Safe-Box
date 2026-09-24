#!/usr/bin/env bash
#
# Release gate: a release tag's DBVERSION component must equal the Room schema version it ships.
#
# Tags follow vMAJOR.MINOR.DBVERSION.FIX (optionally -rcN), and the third component is meant to be
# the Room schema version of the code being tagged. Nothing in the build reads it - versionName is
# the tag with the "v" stripped - so a forgotten bump ships silently. It still matters: the upgrade
# test's `schema-boundary` rule (scripts/resolve-baselines.sh on the harness branch) picks which old
# releases to migrate from by trusting this component, and a mislabelled release makes it test the
# wrong migration path.
#
# The version is read from the @Database annotation, not from the highest file in app/schemas/.
# A 5.json was committed in 331ee64 (January 2026) while the database stayed at 4, and was only
# regenerated for the real version 5 by #241, so "newest exported schema" names the wrong version
# for v2.0.4.0.
#
# This file is meant to be sourced. It can also be run directly:
#   scripts/lib/tag-db-version.sh <tag> [database-source] [schema-dir]

DEFAULT_DATABASE_SOURCE="app/src/main/java/com/andryoga/safebox/data/db/SafeBoxDatabase.kt"
DEFAULT_SCHEMA_DIR="app/schemas/com.andryoga.safebox.data.db.SafeBoxDatabase"

# Prints the DBVERSION component of a release tag.
#
# Only the release shape is accepted. Anything else is rejected rather than guessed at, because a
# tag that happens to start with "v" (release.yml triggers on 'v*') is exactly where a typo would
# otherwise pass: `v2.2.5` would have its "5" read as FIX or DBVERSION depending on the parser.
#
# $1 - the tag, e.g. v2.2.5.0 or v2.2.5.0-rc1
# Returns 1, with a message on stderr, if the tag is not a release tag.
db_version_of_tag() {
    local tag="$1"
    if ! printf '%s' "$tag" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$'; then
        echo "error: '$tag' is not a release tag of the form vMAJOR.MINOR.DBVERSION.FIX[-rcN]." >&2
        return 1
    fi
    printf '%s\n' "$tag" | sed -E 's/^v[0-9]+\.[0-9]+\.([0-9]+)\..*/\1/'
}

# Prints the schema version declared by the @Database annotation.
#
# Only a literal `version = N` inside the annotation is understood. If that ever becomes a constant
# or moves out of the annotation, this fails loudly instead of guessing - and the device-free test
# that parses the real source file fails in the PR that made the change, not on release day.
#
# $1 - path to the Kotlin source declaring the database
# Returns 1, with a message on stderr, unless exactly one literal version is found.
db_version_of_source() {
    local source_file="$1"
    if [ ! -s "$source_file" ]; then
        echo "error: database source '$source_file' is missing or empty." >&2
        return 1
    fi

    local versions count
    versions=$(awk '
        /@Database\(/ { inside = 1 }
        inside && /^[[:space:]]*version[[:space:]]*=[[:space:]]*[0-9]+,?[[:space:]]*$/ {
            gsub(/[^0-9]/, ""); print
        }
        inside && /^\)/ { inside = 0 }
    ' "$source_file")
    count=$(printf '%s' "$versions" | grep -c . || true)

    if [ "$count" -ne 1 ]; then
        echo "error: expected one literal 'version = N' in the @Database annotation of" \
            "'$source_file', found $count." >&2
        return 1
    fi
    printf '%s\n' "$versions"
}

# Fails unless the tag's DBVERSION equals the declared schema version, and that version has an
# exported schema that agrees with it.
#
# The schema check guards the other half of a bump: raising `version` without the exported
# N.json - which Room writes at compile time - means the migration was never compiled against the
# committed schemas, and MigrationTest has nothing to test the new version with.
#
# $1 - the tag being released
# $2 - path to the database source (defaults to the app's)
# $3 - the exported schema directory (defaults to the app's)
check_tag_db_version() {
    local tag="$1"
    local source_file="${2:-$DEFAULT_DATABASE_SOURCE}"
    local schema_dir="${3:-$DEFAULT_SCHEMA_DIR}"

    local tag_db source_db
    tag_db=$(db_version_of_tag "$tag") || return 1
    source_db=$(db_version_of_source "$source_file") || return 1

    if [ "$tag_db" != "$source_db" ]; then
        echo "error: $tag tag is incorrect. Delete the tag and re-tag this commit with DBVERSION $source_db, e.g." >&2
        echo "       $(printf '%s' "$tag" | sed -E "s/^(v[0-9]+\.[0-9]+\.)[0-9]+/\1$source_db/")" >&2
        return 1
    fi

    local schema_file="$schema_dir/$source_db.json"
    if [ ! -s "$schema_file" ]; then
        echo "error: $source_file declares version = $source_db, but $schema_file does not exist." >&2
        echo "       Build once so Room exports it, and commit it with the version bump." >&2
        return 1
    fi
    if ! grep -qE "\"version\"[[:space:]]*:[[:space:]]*$source_db[[:space:]]*," "$schema_file"; then
        echo "error: $schema_file does not declare \"version\": $source_db." >&2
        return 1
    fi

    echo "OK: $tag ships Room schema version $source_db."
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    if [ "$#" -lt 1 ] || [ "$#" -gt 3 ]; then
        echo "usage: $0 <tag> [database-source] [schema-dir]" >&2
        exit 2
    fi
    check_tag_db_version "$@"
fi
