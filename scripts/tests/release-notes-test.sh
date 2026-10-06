#!/usr/bin/env bash
#
# Tests for scripts/lib/release-notes.sh.
#
# The check runs once per release, right before the Play upload, which is far too rarely to notice
# it has stopped working. These cases run on every PR instead, with no toolchain. The last group
# validates every directory committed under distribution/whatsnew/, so a misnamed, empty or
# over-long notes file fails in the PR that added it rather than in the release that would have
# shipped it.
#
# Run: scripts/tests/release-notes-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
# shellcheck source=../lib/release-notes.sh
source "$script_dir/../lib/release-notes.sh"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# $1 expectation: dir|none|fail, $2 case name, $3 the tag, $4 the whatsnew root.
#   dir  - succeeds and prints <root>/<base version of the tag>
#   none - succeeds and prints nothing (the release has no notes)
#   fail - exits non-zero
expect() {
    local expectation="$1" name="$2" tag="$3" root="$4"
    total=$((total + 1))

    local stdout status
    stdout=$(release_notes_dir "$tag" "$root" 2>"$work_dir/stderr")
    status=$?

    local verdict=""
    case "$expectation" in
        dir)
            local wanted="$root/${tag#v}"
            wanted="${wanted%%-*}"
            if [ "$status" -ne 0 ]; then
                verdict="expected the directory, it failed with:"
            elif [ "$stdout" != "$wanted" ]; then
                verdict="expected '$wanted' on stdout, got '$stdout'"
            fi
            ;;
        none)
            if [ "$status" -ne 0 ]; then
                verdict="expected no notes and success, it failed with:"
            elif [ -n "$stdout" ]; then
                verdict="expected nothing on stdout, got '$stdout'"
            elif ! grep -q '^warning: no release notes' "$work_dir/stderr"; then
                verdict="expected a warning on stderr, got:"
            fi
            ;;
        fail)
            if [ "$status" -eq 0 ]; then
                verdict="expected a failure, it passed"
            fi
            ;;
    esac

    if [ -n "$verdict" ]; then
        echo "FAIL  $name: $verdict"
        sed 's/^/      /' "$work_dir/stderr"
        failures=$((failures + 1))
    else
        echo "ok    $name"
    fi
}

# Writes a notes file. $1 - root, $2 - version directory, $3 - file name, $4 - content (printf %b)
notes_file() {
    mkdir -p "$1/$2"
    printf '%b' "$4" > "$1/$2/$3"
}

# A string of exactly $1 ASCII characters.
chars() {
    printf '%*s' "$1" '' | tr ' ' 'x'
}

# Each case gets its own root so the directories cannot leak into one another.
root() {
    local r="$work_dir/$1"
    mkdir -p "$r"
    printf '%s' "$r"
}

# The happy paths, and the RC sharing its stable's notes.
r=$(root valid)
notes_file "$r" 2.2.5.0 whatsnew-en-US "Fixed things.\n"
expect dir  "stable tag with notes"                 v2.2.5.0      "$r"
expect dir  "rc tag reads the stable's directory"   v2.2.5.0-rc3  "$r"
expect none "another version has no directory"      v2.2.6.0      "$r"

r=$(root locales)
notes_file "$r" 2.2.5.0 whatsnew-en-US "English.\n"
notes_file "$r" 2.2.5.0 whatsnew-hi-IN "Hindi.\n"
notes_file "$r" 2.2.5.0 whatsnew-es-419 "Spanish.\n"
notes_file "$r" 2.2.5.0 whatsnew-fil "Filipino.\n"
notes_file "$r" 2.2.5.0 .gitkeep ""
expect dir  "several locales, dotfile ignored"      v2.2.5.0      "$r"

# No directory at all: not an error, the notes are typed in Play Console when promoting.
r=$(root absent)
expect none "root without any version directory"    v2.2.5.0      "$r"
expect none "root that does not exist"              v2.2.5.0      "$work_dir/nowhere"

# Play's length limit, counted exactly as the action sends the file.
r=$(root limit)
notes_file "$r" 2.2.5.0 whatsnew-en-US "$(chars 500)"
expect dir  "exactly 500 characters, no newline"    v2.2.5.0      "$r"
notes_file "$r" 2.2.5.0 whatsnew-en-US "$(chars 499)\n"
expect dir  "499 characters plus the final newline" v2.2.5.0      "$r"
notes_file "$r" 2.2.5.0 whatsnew-en-US "$(chars 500)\n"
expect fail "500 characters plus the final newline" v2.2.5.0      "$r"
notes_file "$r" 2.2.5.0 whatsnew-en-US "$(chars 501)"
expect fail "501 characters"                        v2.2.5.0      "$r"

# A directory that exists must be usable, or the mistake ships silently.
r=$(root empty-file)
notes_file "$r" 2.2.5.0 whatsnew-en-US ""
expect fail "empty notes file"                      v2.2.5.0      "$r"

r=$(root empty-dir)
mkdir -p "$r/2.2.5.0"
expect fail "empty version directory"               v2.2.5.0      "$r"

r=$(root only-dotfile)
notes_file "$r" 2.2.5.0 .gitkeep ""
expect fail "version directory with only a dotfile" v2.2.5.0      "$r"

r=$(root underscore)
notes_file "$r" 2.2.5.0 whatsnew_en-US "Notes.\n"
expect fail "underscore instead of the hyphen"      v2.2.5.0      "$r"

r=$(root extension)
notes_file "$r" 2.2.5.0 whatsnew-en-US.txt "Notes.\n"
expect fail "file extension becomes the language"   v2.2.5.0      "$r"

r=$(root lowercase-region)
notes_file "$r" 2.2.5.0 whatsnew-en-us "Notes.\n"
expect fail "lower-case region"                     v2.2.5.0      "$r"

r=$(root stray)
notes_file "$r" 2.2.5.0 whatsnew-en-US "Notes.\n"
notes_file "$r" 2.2.5.0 README.md "How to write these.\n"
expect fail "stray file beside valid notes"         v2.2.5.0      "$r"

r=$(root nested)
notes_file "$r" 2.2.5.0 whatsnew-en-US "Notes.\n"
mkdir -p "$r/2.2.5.0/drafts"
expect fail "subdirectory inside the version"       v2.2.5.0      "$r"

# Tag shapes release.yml's 'v*' trigger lets through but that are not releases.
r=$(root tags)
notes_file "$r" 2.2.5.0 whatsnew-en-US "Notes.\n"
expect fail "three-component legacy tag"            v1.1.0        "$r"
expect fail "unknown suffix"                        v2.2.5.0-beta "$r"
expect fail "not a v tag"                           mr11-base     "$r"

# The real directory: every committed version must be valid, under the tag that would read it.
real_root="$repo_root/$DEFAULT_WHATSNEW_ROOT"
if [ -d "$real_root" ]; then
    for version_dir in "$real_root"/*/; do
        [ -d "$version_dir" ] || continue
        version=$(basename "$version_dir")
        total=$((total + 1))
        if ! printf '%s' "$version" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
            echo "FAIL  real notes: '$version' is not a MAJOR.MINOR.DBVERSION.FIX directory, no tag will read it"
            failures=$((failures + 1))
        elif ! release_notes_dir "v$version" "$real_root" >/dev/null 2>"$work_dir/stderr"; then
            echo "FAIL  real notes: distribution/whatsnew/$version is invalid:"
            sed 's/^/      /' "$work_dir/stderr"
            failures=$((failures + 1))
        else
            echo "ok    real notes: distribution/whatsnew/$version"
        fi
    done
fi

if [ "$failures" -ne 0 ]; then
    echo "$failures of $total release-notes cases failed"
    exit 1
fi
echo "All $total release-notes cases passed"
