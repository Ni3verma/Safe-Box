#!/usr/bin/env bash
#
# Shared facts about the committed backup files, for every script that touches them:
# scripts/tests/backup-format-test.sh, scripts/update-seed.sh and the two harness runners.
#
# One place, because each of these facts is needed by at least three scripts and by the Kotlin
# harness, and two copies of a path or a password eventually disagree. Source it; it defines
# variables and functions only. Paths are relative to the repository root.

# The hand-captured backup in the current format. The path never changes: the upgrade test reads
# older releases' copies through it with `git show <tag>:$SEED_FILE`.
SEED_FILE="upgrade-test/seed/seed.bak"
SEED_README="upgrade-test/seed/README.md"

# One frozen backup per older format, format-<k>.bak for every k below BACKUP_VERSION.
FIXTURES_DIR="upgrade-test/fixtures"
FIXTURES_README="$FIXTURES_DIR/README.md"

# Every capture uses this as its backup password (and as the master password of the vault it was
# captured from). It satisfies every release's password rules, including v1's stricter ones.
FIXED_BACKUP_PASSWORD="Upgrade@@Test123"

# format-2.bak predates the fixed password and cannot be re-encrypted without either script-made
# bytes or a round trip through an old app that may normalise its deliberate ""/null mix.
FORMAT_2_PASSWORD="Fixture@Backup1"

EXPORT_CLASSES_DIR="app/src/main/java/com/andryoga/safebox/data/db/docs/export"
COMMON_CONSTANTS="app/src/main/java/com/andryoga/safebox/common/CommonConstants.kt"
BACKUP_FORMAT_LOCK="app/backup-format.lock"

# The password a committed backup of format $1 was taken under.
password_for_format() {
    if [ "$1" = "2" ]; then
        printf '%s\n' "$FORMAT_2_PASSWORD"
    else
        printf '%s\n' "$FIXED_BACKUP_PASSWORD"
    fi
}

# BACKUP_VERSION as declared in the app's source.
backup_version_in_code() {
    sed -n 's/^[[:space:]]*const val BACKUP_VERSION = \([0-9]\{1,\}\).*/\1/p' "$COMMON_CONSTANTS" | head -n 1
}

# Runs scripts/InspectBackup.java with the JDK from JAVA_HOME, or whatever `java` is on PATH.
inspect_backup() {
    "${JAVA_HOME:+$JAVA_HOME/bin/}java" "$(dirname "${BASH_SOURCE[0]}")/../InspectBackup.java" "$@"
}

# SHA-256 of stdin, on both macOS (shasum) and Linux (sha256sum).
sha256_of_stdin() {
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum | awk '{print $1}'
    else
        shasum -a 256 | awk '{print $1}'
    fi
}

# SHA-256 of the export classes after dropping everything that cannot change the backup's JSON:
# comments, package and import lines, and all whitespace. Rewording a KDoc therefore never forces
# a BACKUP_VERSION bump. A "//" inside a string literal would be stripped too; the export classes
# hold no string literals, and a data-class change that added one would still change the hash.
export_classes_hash() {
    find "$EXPORT_CLASSES_DIR" -maxdepth 1 -name '*.kt' | sort | while IFS= read -r file; do
        basename "$file"
        cat "$file"
    done | perl -0777 -pe 's{/\*.*?\*/}{}gs; s{//[^\n]*}{}g; s{^[ \t]*(package|import)[^\n]*}{}mg; s{\s+}{}g' |
        sha256_of_stdin
}
