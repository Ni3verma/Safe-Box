#!/usr/bin/env bash
#
# Downloads the SafeBox-qa.apk attached to a release tag, for use as N-1 by
# scripts/run-upgrade-test.sh.
#
# `release.yml` attaches the QA APK to every release, so GitHub Releases is already a permanent,
# addressable archive of every shipped QA build back to v1.3.3.0; nothing is archived separately.
# The signing certificate is not checked here: run-upgrade-test.sh checks it (assert_qa_apk in
# scripts/lib/harness.sh) for both APKs, which also covers an N-1 APK supplied by hand.
#
# Usage: scripts/fetch-baseline-apk.sh <tag> <destination-dir>
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <tag> <destination-dir>" >&2
    exit 2
fi

tag="$1"
dest="$2"
repo="${UPGRADE_TEST_REPO:-Ni3verma/Safe-Box}"
apk="$dest/SafeBox-qa.apk"

mkdir -p "$dest"
# gh exits 0 when a pattern matches no asset, and `--clobber` only replaces a file it downloads,
# so an APK left by an earlier run would pass the check below as this tag's. Removed first.
rm -f "$apk"
gh release download "$tag" --repo "$repo" --pattern 'SafeBox-qa.apk' --dir "$dest" --clobber

# A missing asset would otherwise leave the harness to fail later with a confusing adb error.
if [ ! -s "$apk" ]; then
    echo "error: $tag has no SafeBox-qa.apk asset (or the download was empty)." >&2
    echo "       v1.0.0, v1.1.0 and v1.2.2.0 predate the upload step and have none." >&2
    exit 1
fi

# A truncated download is still a file. An APK is a zip, so this costs nothing and turns a corrupt
# artifact into a clear message rather than an INSTALL_PARSE_FAILED five minutes later.
#
# The two failures are separated on purpose: without the first check, a machine with no unzip
# reports every download as corrupt, which is the wrong thing to go looking for. The check is not
# skipped when the tool is missing - a silently unverified baseline is what this exists to prevent.
if ! command -v unzip > /dev/null 2>&1; then
    echo "error: unzip is required to verify the downloaded APK, and is not installed." >&2
    exit 1
fi
if ! unzip -l "$apk" AndroidManifest.xml > /dev/null 2>&1; then
    echo "error: $apk is not a readable APK - the download is corrupt." >&2
    exit 1
fi

echo "Baseline $tag downloaded to $apk"
