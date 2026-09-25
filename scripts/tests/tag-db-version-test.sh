#!/usr/bin/env bash
#
# Tests for scripts/lib/tag-db-version.sh.
#
# The gate runs once per release, on a tag push, which is far too rarely to discover that it has
# stopped working. These cases run on every PR instead, in seconds and with no toolchain. The last
# group parses the *real* database source and schema directory, so a change to how the version is
# declared breaks this suite in the PR that made it rather than blocking a release later.
#
# Run: scripts/tests/tag-db-version-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
# shellcheck source=../lib/tag-db-version.sh
source "$script_dir/../lib/tag-db-version.sh"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# $1 expectation: pass|fail, $2 case name, remaining args: check_tag_db_version arguments
expect() {
    local expectation="$1" name="$2"
    shift 2
    total=$((total + 1))

    local output status
    output=$(check_tag_db_version "$@" 2>&1)
    status=$?

    if [ "$expectation" = "pass" ] && [ "$status" -ne 0 ]; then
        echo "FAIL  $name: expected the gate to pass, it failed with:"
        printf '%s\n' "$output" | sed 's/^/      /'
        failures=$((failures + 1))
    elif [ "$expectation" = "fail" ] && [ "$status" -eq 0 ]; then
        echo "FAIL  $name: expected the gate to fail, it passed"
        failures=$((failures + 1))
    else
        echo "ok    $name"
    fi
}

# Writes a database source declaring the given annotation body line, and returns its path.
# $1 - file name, $2 - the line to put where `version = N` normally sits
database_source() {
    local file="$work_dir/$1.kt"
    cat > "$file" <<EOF
@Database(
    entities = [
        LoginDataEntity::class,
    ],
$2
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase()
EOF
    printf '%s\n' "$file"
}

schemas="$work_dir/schemas"
mkdir -p "$schemas"
printf '{\n  "formatVersion": 1,\n  "database": {\n    "version": 5,\n    "identityHash": "x"\n  }\n}\n' \
    > "$schemas/5.json"
# 6.json exists but claims version 4, standing in for a stale or hand-copied export.
printf '{\n  "formatVersion": 1,\n  "database": {\n    "version": 4,\n    "identityHash": "x"\n  }\n}\n' \
    > "$schemas/6.json"

v5=$(database_source v5 "    version = 5,")
v5_last=$(database_source v5-no-comma "    version = 5")
v6=$(database_source v6 "    version = 6,")
v7=$(database_source v7 "    version = 7,")
constant=$(database_source constant "    version = DATABASE_VERSION,")
twice=$(database_source twice "    version = 5,
    version = 5,")

# The rule itself.
expect pass "matching release tag"               v2.2.5.0      "$v5" "$schemas"
expect pass "matching rc tag"                    v2.2.5.0-rc3  "$v5" "$schemas"
expect pass "version as the last argument"       v2.2.5.1      "$v5_last" "$schemas"
expect fail "forgotten bump (tag behind)"        v2.2.4.0      "$v5" "$schemas"
expect fail "over-bumped tag (tag ahead)"        v2.2.6.0      "$v5" "$schemas"
expect fail "FIX component mistaken for DB"      v2.2.4.5      "$v5" "$schemas"

# Tag shapes release.yml's 'v*' trigger lets through but that are not releases.
expect fail "three-component legacy tag"         v1.1.0        "$v5" "$schemas"
expect fail "unknown suffix"                     v2.2.5.0-beta "$v5" "$schemas"
expect fail "not a v tag"                        mr11-base     "$v5" "$schemas"

# The source must be understood, or the gate refuses rather than guessing.
expect fail "version behind a constant"          v2.2.5.0      "$constant" "$schemas"
expect fail "version declared twice"             v2.2.5.0      "$twice" "$schemas"
expect fail "missing database source"            v2.2.5.0      "$work_dir/absent.kt" "$schemas"

# The exported schema has to exist and agree.
expect fail "bumped without exporting a schema"  v2.2.7.0      "$v7" "$schemas"
expect fail "schema file of the wrong version"   v2.2.6.0      "$v6" "$schemas"

# The real files. Whatever the current version is, the tag built from it must pass and its
# neighbours must not.
real_db=$(db_version_of_source "$repo_root/$DEFAULT_DATABASE_SOURCE")
if [ -z "$real_db" ]; then
    total=$((total + 1))
    echo "FAIL  real source: could not read the version from $DEFAULT_DATABASE_SOURCE"
    failures=$((failures + 1))
else
    real_source="$repo_root/$DEFAULT_DATABASE_SOURCE"
    real_schemas="$repo_root/$DEFAULT_SCHEMA_DIR"
    expect pass "real source, matching tag"      "v9.9.$real_db.0"          "$real_source" "$real_schemas"
    expect fail "real source, stale tag"         "v9.9.$((real_db - 1)).0"  "$real_source" "$real_schemas"
fi

if [ "$failures" -ne 0 ]; then
    echo "$failures of $total tag-db-version cases failed"
    exit 1
fi
echo "All $total tag-db-version cases passed"
