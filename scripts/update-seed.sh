#!/usr/bin/env bash
#
# Installs a newly captured backup as the upgrade-test seed, and archives the outgoing seed as a
# fixture when the format has changed.
#
# Usage: scripts/update-seed.sh <captured.bak> "<captured from>"
#   <captured from> names the build, e.g. "debug build of PR #281 (a1b2c3d)"
#
# What it does, in order, stopping at the first problem:
#   1. Refuses a file that is not in the current format (BACKUP_VERSION) or does not decode with
#      the fixed backup password.
#   2. If the current seed is an older format k, copies it to upgrade-test/fixtures/format-<k>.bak
#      and records its provenance in the fixtures README, so the restore test keeps covering it.
#   3. Copies the new file to upgrade-test/seed/seed.bak.
#   4. Regenerates the seed README and the fixtures README's generated contents from the decoded
#      files, so neither can drift from what is committed.
#
# The capture procedure is in docs/testing/upgrade-harness-operations.md#capturing-a-seed.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
cd "$repo_root"
# shellcheck source=lib/backup-files.sh
source "$script_dir/lib/backup-files.sh"

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <captured.bak> \"<captured from>\"" >&2
    exit 2
fi
captured="$1"
captured_from="$2"
today=$(date +%Y-%m-%d)

version=$(backup_version_in_code)
captured_version=$(inspect_backup --header "$captured")
if [ "$captured_version" != "$version" ]; then
    echo "error: $captured is format $captured_version, but BACKUP_VERSION is $version." >&2
    echo "       Capture it on a build of this branch." >&2
    exit 1
fi
if ! inspect_backup --canonical "$captured" "$FIXED_BACKUP_PASSWORD" > /dev/null; then
    echo "error: $captured does not decode with the fixed backup password $FIXED_BACKUP_PASSWORD." >&2
    exit 1
fi

# Markdown summary of one backup: format, hash, counts, and which fields each record fills.
# Field values are left out on purpose; InspectBackup prints them.
#
# $1 - the .bak, $2 - its password
contents_markdown() {
    local file="$1" password="$2"
    echo "- Format version: $(inspect_backup --header "$file")"
    echo "- SHA-256: \`$(sha256_of_stdin < "$file")\`"
    inspect_backup --canonical "$file" "$password" | perl -ne '
        next unless /^([A-Z_]+)\|("(?:[^"\\]|\\.)*")\|(-?\d+)\|(\w+)=(.*)$/;
        my ($type, $title, $created, $field, $value) = ($1, $2, $3, $4, $5);
        my $key = "$type\t$title\t$created";
        $records{$key} ||= [];
        # Some forms store "" for untouched fields; that is not a filled field.
        push @{$records{$key}}, $field unless $field =~ /^(title|creationDate|updateDate)$/ || $value eq q("");
        END {
            my %count;
            $count{(split /\t/, $_)[0]}++ for keys %records;
            print "- Records: ", join(", ", map { "$count{$_} $_" } sort keys %count), "\n\n";
            print "| Type | Title | Filled fields |\n|---|---|---|\n";
            for my $key (sort keys %records) {
                my ($type, $title) = split /\t/, $key;
                $title =~ s/\|/\\|/g;
                print "| $type | $title | ", join(", ", sort @{$records{$key}}), " |\n";
            }
        }'
}

# Step 2: archive the outgoing seed when the format moved on.
if [ -s "$SEED_FILE" ]; then
    old_version=$(inspect_backup --header "$SEED_FILE")
    if [ "$old_version" != "$version" ]; then
        archive="$FIXTURES_DIR/format-$old_version.bak"
        if [ -e "$archive" ] && ! cmp -s "$SEED_FILE" "$archive"; then
            echo "error: $archive already exists and differs from the outgoing seed." >&2
            echo "       Old-format files are never replaced; resolve this by hand." >&2
            exit 1
        fi
        cp "$SEED_FILE" "$archive"
        old_from=$(sed -n 's/^- Captured from: //p' "$SEED_README")
        old_on=$(sed -n 's/^- Captured on: //p' "$SEED_README")
        row="| \`format-$old_version.bak\` | $old_from | $old_on | \`$FIXED_BACKUP_PASSWORD\` |"
        awk -v row="$row" '/^<!-- provenance rows end -->$/ { print row } { print }' \
            "$FIXTURES_README" > "$FIXTURES_README.tmp"
        mv "$FIXTURES_README.tmp" "$FIXTURES_README"
        echo "archived the format-$old_version seed as $archive"
    fi
fi

# Step 3: install.
mkdir -p "$(dirname "$SEED_FILE")"
cp "$captured" "$SEED_FILE"

# Step 4: regenerate the READMEs.
{
    echo "# Upgrade-test seed"
    echo
    echo "The backup the upgrade test restores into N-1 when this commit's release is N-1, and the"
    echo "current-format file the restore test restores into N. Replace it only with"
    echo "\`scripts/update-seed.sh\`; the procedure is in"
    echo "[upgrade-harness-operations.md](../../docs/testing/upgrade-harness-operations.md#capturing-a-seed)."
    echo
    echo "- Captured from: $captured_from"
    echo "- Captured on: $today"
    echo "- Backup password: \`$FIXED_BACKUP_PASSWORD\`"
    echo
    echo "## Contents (generated by update-seed.sh, do not edit)"
    echo
    contents_markdown "$SEED_FILE" "$FIXED_BACKUP_PASSWORD"
} > "$SEED_README"

marker="<!-- generated contents start: scripts/update-seed.sh rewrites everything below -->"
{
    # Literal comparison: the marker contains slashes, which would end a sed address early.
    awk -v marker="$marker" '{ print } $0 == marker { exit }' "$FIXTURES_README"
    for fixture in "$FIXTURES_DIR"/format-*.bak; do
        k=$(basename "$fixture" .bak)
        k=${k#format-}
        echo
        echo "### \`format-$k.bak\`"
        echo
        contents_markdown "$fixture" "$(password_for_format "$k")"
    done
} > "$FIXTURES_README.tmp"
if ! grep -qxF "$marker" "$FIXTURES_README.tmp"; then
    rm -f "$FIXTURES_README.tmp"
    echo "error: $FIXTURES_README lost its generated-contents marker line; restore it by hand." >&2
    exit 1
fi
mv "$FIXTURES_README.tmp" "$FIXTURES_README"

echo "installed $SEED_FILE (format $version); READMEs regenerated. Review the diff and commit."
