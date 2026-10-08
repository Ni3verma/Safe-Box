#!/usr/bin/env bash
#
# Tests for scripts/translations.py.
#
# Verifies deterministic local auto-sync (key renames, deletions, translatable="false"
# removals), non-blocking local warnings for new/modified English strings, hard structural
# failures (apostrophe escaping, format placeholders, invariant brand/crypto tokens, nav-bar
# length limits), and finally checks the real committed app/src/main/res/ translations.
#
# Run: ./scripts/tests/translations-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
tool="$repo_root/scripts/translations.py"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

pass() {
    total=$((total + 1))
    echo "PASS: $1"
}

fail() {
    total=$((total + 1))
    failures=$((failures + 1))
    echo "FAIL: $1 — $2" >&2
}

# Helper to create a clean synthetic fixture workspace
setup_fixture() {
    local dir="$work_dir/$1"
    mkdir -p "$dir/res/values" "$dir/res/values-es" "$dir/res/values-fr"
    cat > "$dir/res/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name" translatable="false">Safe Box</string>
    <string name="greeting">Welcome to Safe Box</string>
    <string name="backup_path">Backup path is %1$s</string>
    <string name="bottom_nav_records">Records</string>
    <string name="old_key">Delete this record?</string>
</resources>
EOF
    cat > "$dir/res/values-es/strings.xml" <<'EOF'
<resources>
    <string name="greeting">Bienvenido a Safe Box</string>
    <string name="backup_path">La ruta de copia es %1$s</string>
    <string name="bottom_nav_records">Registros</string>
    <string name="old_key">¿Eliminar este registro?</string>
</resources>
EOF
    cat > "$dir/res/values-fr/strings.xml" <<'EOF'
<resources>
    <string name="greeting">Bienvenue sur Safe Box</string>
    <string name="backup_path">Le chemin de sauvegarde est %1$s</string>
    <string name="bottom_nav_records">Entrées</string>
    <string name="old_key">Supprimer cet élément ?</string>
</resources>
EOF
    python3 "$tool" --update-lock --res-dir "$dir/res" --lock-file "$dir/translations.lock" >/dev/null 2>&1
    echo "$dir"
}

# 1. Clean baseline passes --ci-check
d=$(setup_fixture "clean")
if python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "clean baseline passes --ci-check"
else
    fail "clean baseline passes --ci-check" "unexpected non-zero exit"
fi

# 2. Key rename in English is auto-renamed across all locales by --local-sync
d=$(setup_fixture "rename")
sed 's/name="old_key"/name="renamed_key"/' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
if python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1 &&
   grep -q 'name="renamed_key"' "$d/res/values-es/strings.xml" &&
   grep -q 'name="renamed_key"' "$d/res/values-fr/strings.xml" &&
   ! grep -q 'name="old_key"' "$d/res/values-es/strings.xml" &&
   python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "--local-sync auto-renames 1-to-1 renamed keys and updates lock"
else
    fail "--local-sync auto-renames 1-to-1 renamed keys and updates lock" "rename did not propagate cleanly"
fi

# 3. Key deletion in English is auto-deleted across all locales by --local-sync
d=$(setup_fixture "delete")
grep -v 'name="old_key"' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
if python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1 &&
   ! grep -q 'name="old_key"' "$d/res/values-es/strings.xml" &&
   ! grep -q 'name="old_key"' "$d/res/values-fr/strings.xml" &&
   python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "--local-sync auto-deletes removed keys and updates lock"
else
    fail "--local-sync auto-deletes removed keys and updates lock" "deleted key remained"
fi

# 4. Adding translatable="false" strips key from all locales on --local-sync
d=$(setup_fixture "nontrans")
sed 's/name="old_key"/name="old_key" translatable="false"/' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
if python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1 &&
   ! grep -q 'name="old_key"' "$d/res/values-es/strings.xml" &&
   python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "--local-sync strips newly marked translatable=false keys from locales"
else
    fail "--local-sync strips newly marked translatable=false keys from locales" "non-translatable key remained"
fi

# 5. Adding a brand-new English string succeeds with warning on --local-sync, fails on --ci-check
d=$(setup_fixture "new_string")
sed 's|</resources>|    <string name="brand_new">Brand new text</string>\n</resources>|' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
local_out=$(python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" 2>&1)
local_rc=$?
ci_out=$(python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" 2>&1)
ci_rc=$?
if [ "$local_rc" -eq 0 ] && echo "$local_out" | grep -q ".agents/skills/localization/SKILL.md" &&
   [ "$ci_rc" -ne 0 ] && echo "$ci_out" | grep -q ".agents/skills/localization/SKILL.md"; then
    pass "new English string warns without failing locally, fails in CI, and points to SKILL.md"
else
    fail "new English string warns without failing locally, fails in CI, and points to SKILL.md" "local_rc=$local_rc ci_rc=$ci_rc"
fi

# 6. Modifying an existing English string's wording fails --ci-check (stale translation detection)
d=$(setup_fixture "stale_string")
sed 's/Delete this record?/Permanently erase this record now?/' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
if ! python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "modified English wording fails --ci-check until translations are updated"
else
    fail "modified English wording fails --ci-check until translations are updated" "stale translation was not caught"
fi

# 7. Mismatched format placeholder (%1$d instead of %1$s) fails both --local-sync and --ci-check
d=$(setup_fixture "bad_placeholder")
sed 's/%1\$s/%1\$d/' "$d/res/values-es/strings.xml" > "$d/res/values-es/strings.xml.tmp"
mv "$d/res/values-es/strings.xml.tmp" "$d/res/values-es/strings.xml"
if ! python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1 &&
   ! python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "mismatched format placeholder fails both --local-sync and --ci-check"
else
    fail "mismatched format placeholder fails both --local-sync and --ci-check" "did not fail"
fi

# 8. Unescaped apostrophe in unquoted Android string fails both --local-sync and --ci-check
d=$(setup_fixture "bad_apostrophe")
sed "s/Bienvenue sur Safe Box/L'application Safe Box/" "$d/res/values-fr/strings.xml" > "$d/res/values-fr/strings.xml.tmp"
mv "$d/res/values-fr/strings.xml.tmp" "$d/res/values-fr/strings.xml"
if ! python3 "$tool" --local-sync --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "unescaped apostrophe fails --local-sync"
else
    fail "unescaped apostrophe fails --local-sync" "did not fail"
fi

# 9. Missing invariant token ('Safe Box' translated away) fails
d=$(setup_fixture "missing_brand")
sed 's/Safe Box/Caja Fuerte/' "$d/res/values-es/strings.xml" > "$d/res/values-es/strings.xml.tmp"
mv "$d/res/values-es/strings.xml.tmp" "$d/res/values-es/strings.xml"
if ! python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "missing invariant token ('Safe Box') fails verification"
else
    fail "missing invariant token ('Safe Box') fails verification" "did not fail"
fi

# 10. --update-lock restores previous lock when verification fails
d=$(setup_fixture "update_lock_rollback")
before_lock=$(cat "$d/translations.lock")
sed 's/Delete this record?/Permanently erase this record?/' "$d/res/values/strings.xml" > "$d/res/values/strings.xml.tmp"
mv "$d/res/values/strings.xml.tmp" "$d/res/values/strings.xml"
sed 's/%1\$s/%1\$d/' "$d/res/values-es/strings.xml" > "$d/res/values-es/strings.xml.tmp"
mv "$d/res/values-es/strings.xml.tmp" "$d/res/values-es/strings.xml"
if ! python3 "$tool" --update-lock --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1 &&
   [ "$(cat "$d/translations.lock")" = "$before_lock" ]; then
    pass "--update-lock restores previous lock when verification fails"
else
    fail "--update-lock restores previous lock when verification fails" "lock file was modified despite failure"
fi

# 11. Non-locale values-* directories (e.g. values-night) are ignored
d=$(setup_fixture "values_night")
mkdir -p "$d/res/values-night"
cat > "$d/res/values-night/strings.xml" <<'EOF'
<resources>
    <string name="unrelated_theme_string">Dark</string>
</resources>
EOF
if python3 "$tool" --ci-check --res-dir "$d/res" --lock-file "$d/translations.lock" >/dev/null 2>&1; then
    pass "non-locale values-* directories (values-night) are ignored"
else
    fail "non-locale values-* directories (values-night) are ignored" "values-night was treated as a locale"
fi

# 12. Real repository verification
if (cd "$repo_root" && python3 "$tool" --ci-check); then
    pass "real repository app/src/main/res/ passes --ci-check"
else
    fail "real repository app/src/main/res/ passes --ci-check" "committed locale files failed verification"
fi

echo ""
echo "Summary: $((total - failures))/$total passed."
[ "$failures" -eq 0 ]
