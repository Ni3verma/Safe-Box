#!/usr/bin/env bash
#
# Unit tests for the canonical dump in scripts/InspectBackup.java.
#
# The upgrade and restore tests pass or fail on a diff of two canonical dumps, so the rules that
# decide what "equal" means are pinned by scripts/tests/InspectBackupTest.java. This wrapper
# compiles the tool and its test together (the test calls the tool's package-private methods) and
# runs it. Plain JDK, no emulator, a few seconds.
#
# Run: scripts/tests/inspect-backup-test.sh
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT

"$javac_bin" -d "$classes" "$script_dir/../InspectBackup.java" "$script_dir/InspectBackupTest.java"
"$java_bin" -cp "$classes" InspectBackupTest
