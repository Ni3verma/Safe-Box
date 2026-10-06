#!/usr/bin/env bash
#
# Release notes for the Play upload: picks the "what's new" directory for a release tag.
#
# release.yml uploads app-release.aab to Play's closed and open testing tracks on every tag, and the
# release notes it attaches are read from distribution/whatsnew/<MAJOR.MINOR.DBVERSION.FIX>/whatsnew-<locale>.
# The directory is keyed by the tag's base version, so an RC and its stable share one set and a
# previous release's notes can never ship by mistake - there is no "current" file to forget to
# update. An absent directory is not an error: the release is uploaded without notes and they are
# typed in Play Console when the release is promoted, which is where they get a final look anyway.
#
# A directory that does exist has to be right, because the two mistakes it invites are silent:
# the upload action (r0adkll/upload-google-play, src/whatsnew.ts) takes everything after
# "whatsnew-" as the language and ignores any other file name, so a typo drops the notes or sends
# a language Play rejects; and Play's 500-character limit per locale is enforced only when the
# edit is committed, after the bundle has been uploaded.
#
# Prints the directory on stdout when notes exist and nothing when they do not. Meant to be
# sourced; can also be run directly:
#   scripts/lib/release-notes.sh <tag> [whatsnew-root]

DEFAULT_WHATSNEW_ROOT="distribution/whatsnew"
# Play's limit per locale. The whole file is sent verbatim, final newline included, so it counts.
# `wc -m` counts characters under a UTF-8 locale (CI runners are C.UTF-8) and bytes under others,
# which can only over-count multi-byte text, so a wrong locale fails safe.
MAX_RELEASE_NOTES_CHARS=500

# Prints the base version of a release tag: v2.2.5.0 and v2.2.5.0-rc3 both give 2.2.5.0.
#
# Only the release shape is accepted, the same one db_version_of_tag in tag-db-version.sh checks;
# keep the two in step. The gate there has already rejected anything else by the time this runs.
#
# $1 - the tag
# Returns 1, with a message on stderr, if the tag is not a release tag.
release_version_of_tag() {
    local tag="$1"
    if ! printf '%s' "$tag" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$'; then
        echo "error: '$tag' is not a release tag of the form vMAJOR.MINOR.DBVERSION.FIX[-rcN]." >&2
        return 1
    fi
    printf '%s\n' "$tag" | sed -E 's/^v([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+).*/\1/'
}

# Fails unless every entry of a notes directory is a whatsnew-<locale> file Play will accept.
#
# The locale shape is the one Play's listing languages use (en-US, hi-IN, es-419, fil), which is
# narrower than BCP 47 on purpose: the action sends the suffix as-is, so whatsnew-en-US.txt would
# reach Play as language "en-US.txt". Dotfiles are skipped, as the action skips them.
#
# $1 - the directory
check_release_notes_dir() {
    local dir="$1"
    local entry name chars found=0
    for entry in "$dir"/* "$dir"/.[!.]*; do
        [ -e "$entry" ] || continue
        name=$(basename "$entry")
        case "$name" in
            .*) continue ;;
        esac
        if [ ! -f "$entry" ]; then
            echo "error: $entry is not a file; a notes directory holds only whatsnew-<locale> files." >&2
            return 1
        fi
        if ! printf '%s' "$name" | grep -qE '^whatsnew-[a-z]{2,3}(-([A-Z]{2}|[0-9]{3}))?$'; then
            echo "error: $entry is not named whatsnew-<locale> (e.g. whatsnew-en-US);" \
                "the upload would silently skip it or send a language Play rejects." >&2
            return 1
        fi
        if [ ! -s "$entry" ]; then
            echo "error: $entry is empty. Write the notes, or delete the directory to upload without." >&2
            return 1
        fi
        chars=$(wc -m < "$entry" | tr -d '[:space:]')
        if [ "$chars" -gt "$MAX_RELEASE_NOTES_CHARS" ]; then
            echo "error: $entry is $chars characters; Play allows $MAX_RELEASE_NOTES_CHARS per locale," \
                "counting the final newline." >&2
            return 1
        fi
        found=$((found + 1))
    done
    if [ "$found" -eq 0 ]; then
        echo "error: $dir exists but holds no whatsnew-<locale> file. Add one, or delete the directory" \
            "to upload without notes." >&2
        return 1
    fi
}

# Prints the notes directory for a tag, or nothing when the release has no notes.
#
# $1 - the tag being released
# $2 - the root holding one directory per version (defaults to distribution/whatsnew)
# Returns 1, with a message on stderr, for a bad tag or a directory that exists but is invalid.
release_notes_dir() {
    local tag="$1"
    local root="${2:-$DEFAULT_WHATSNEW_ROOT}"

    local version
    version=$(release_version_of_tag "$tag") || return 1

    local dir="$root/$version"
    if [ ! -d "$dir" ]; then
        local message="no release notes for $tag at $dir - uploading without; add them in Play Console when promoting."
        if [ -n "${GITHUB_ACTIONS:-}" ]; then
            echo "::warning::$message" >&2
        else
            echo "warning: $message" >&2
        fi
        return 0
    fi

    check_release_notes_dir "$dir" || return 1
    printf '%s\n' "$dir"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
        echo "usage: $0 <tag> [whatsnew-root]" >&2
        exit 2
    fi
    release_notes_dir "$@"
fi
