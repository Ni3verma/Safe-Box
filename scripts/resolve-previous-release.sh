#!/usr/bin/env bash
#
# Resolves N-1 for the upgrade test: prints its tag and writes its seed backup.
#
# Usage: scripts/resolve-previous-release.sh <tag-under-test|HEAD> <destination-dir>
#   HEAD           a build that is not tagged (a manual run from a branch): N-1 is the newest
#                  stable release
#   writes         <destination-dir>/previous-seed.bak
#   prints         N-1's tag, for scripts/fetch-baseline-apk.sh
#
# Requires an authenticated `gh` and the release tags locally (`git fetch --tags`). The rule and
# the seed fallback live in scripts/lib/previous-release.sh.
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <tag-under-test|HEAD> <destination-dir>" >&2
    exit 2
fi
if [ ! -f settings.gradle ]; then
    echo "error: run this from the repository root." >&2
    exit 2
fi

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/previous-release.sh
source "$script_dir/lib/previous-release.sh"

under_test="$1"
if [ "$under_test" = "HEAD" ]; then
    under_test=""
fi
dest="$2"
repo="${UPGRADE_TEST_REPO:-Ni3verma/Safe-Box}"

# `gh release list` cannot return assets, and a release without the QA APK (v1.0.0, v1.1.0,
# v1.2.2.0) cannot be installed as N-1. Paginated: a page holds 100 releases, and N-1 must not
# depend on which page it falls on. Order does not matter; previous_release sorts.
candidates=$(
    gh api --paginate "repos/$repo/releases?per_page=100" \
        --jq '.[]
              | select(.draft == false and .prerelease == false)
              | select(any(.assets[]; .name == "SafeBox-qa.apk"))
              | .tag_name'
)

previous=$(printf '%s\n' "$candidates" | previous_release "$under_test")
mkdir -p "$dest"
previous_seed "$previous" "$dest/previous-seed.bak"
printf '%s\n' "$previous"
