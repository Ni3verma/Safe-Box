#!/usr/bin/env python3
"""
Safe-Box localization sync and verification tool.

Modes:
  --local-sync   Run automatically during local Gradle builds (preBuild).
                 Deterministically propagates key deletions, 1-to-1 key renames
                 (where the English text is unchanged), and translatable="false"
                 removals across all values-*/strings.xml files and updates
                 app/translations.lock.
                 Warns (without failing the build) if new or modified English
                 strings need AI translation.
                 Fails only on crash-inducing defects (broken XML, unescaped
                 apostrophes, mismatched format placeholders, or invariant
                 brand/crypto token corruption).

  --ci-check     Run in CI (.github/workflows/ci.yml) and pre-release checks.
                 Never mutates files. Fails on any missing translation, stale
                 translation (English text changed relative to translations.lock),
                 extra/non-translatable key in locale files, or structural defect.

  --update-lock  Run after translating new/updated keys across all locales
                 (see .agents/skills/localization/SKILL.md). Verifies all locale
                 files pass structural and key-parity checks, then updates
                 app/translations.lock to match values/strings.xml.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Set, Tuple

SKILL_PATH = ".agents/skills/localization/SKILL.md"

# Brand and cryptographic identifiers that must remain verbatim whenever they
# appear in the English source string.
INVARIANT_TOKENS: Tuple[str, ...] = (
    "Safe Box",
    "SafeBox",
    "GitHub",
    "Base32",
    "SHA1",
    "SHA256",
    "SHA512",
    "TOTP",
    "IFSC",
    "MICR",
)

# Matches Java/Android format specifiers such as %s, %d, %1$s, %1$d, %%
FORMAT_SPECIFIER_RE = re.compile(r"%(?:(\d+)\$)?([sd]|%)")

# Matches an unescaped apostrophe or double-quote inside an unquoted Android string resource.
UNESCAPED_APOSTROPHE_RE = re.compile(r"(?<!\\)'")

# Compact navigation bar keys that must stay short enough to avoid 2-line wrapping/clipping.
COMPACT_NAV_KEYS: Dict[str, int] = {
    "bottom_nav_records": 24,
    "bottom_nav_backup_and_restore": 28,
    "bottom_nav_settings": 24,
}


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def parse_strings_xml(path: Path) -> Tuple[Dict[str, str], Set[str]]:
    """
    Parse an Android strings.xml file.

    Returns:
      (translatable_map, non_translatable_keys)
      where translatable_map maps string name -> raw XML text content.
    """
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        raise ValueError(f"{path}: invalid XML: {exc}") from exc

    root = tree.getroot()
    if root.tag != "resources":
        raise ValueError(f"{path}: root element must be <resources>, got <{root.tag}>")

    translatable: Dict[str, str] = {}
    non_translatable: Set[str] = set()
    seen: Set[str] = set()

    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        if not name:
            raise ValueError(f"{path}: <string> element missing 'name' attribute")
        if name in seen:
            raise ValueError(f"{path}: duplicate <string name=\"{name}\">")
        seen.add(name)

        # Collect inner text (including any inline XML if ever present, though Safe-Box uses plain text)
        raw_text = "".join(elem.itertext())
        if elem.attrib.get("translatable") == "false":
            non_translatable.add(name)
        else:
            translatable[name] = raw_text

    return translatable, non_translatable


def extract_placeholders(raw_text: str) -> List[str]:
    """
    Normalize format placeholders into positional form (e.g. %s -> %1$s) so
    multi-placeholder reordering in target languages compares accurately.
    """
    auto_index = 0
    tokens: List[str] = []
    for match in FORMAT_SPECIFIER_RE.finditer(raw_text):
        pos, conv = match.group(1), match.group(2)
        if conv == "%":
            continue
        if pos is not None:
            tokens.append(f"%{pos}${conv}")
        else:
            auto_index += 1
            tokens.append(f"%{auto_index}${conv}")
    return sorted(tokens)


def check_android_escaping(locale_file: Path, key: str, raw_text: str) -> List[str]:
    """Check Android resource compiler (aapt2) string escaping rules."""
    errors: List[str] = []
    stripped = raw_text.strip()
    is_double_quoted = len(stripped) >= 2 and stripped.startswith('"') and stripped.endswith('"')
    if not is_double_quoted and UNESCAPED_APOSTROPHE_RE.search(raw_text):
        errors.append(
            f"{locale_file} [{key}]: unescaped apostrophe ('); use \\' or wrap string in double quotes"
        )
    return errors


LOCALE_DIR_RE = re.compile(r"^values-([a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)$")


def discover_locale_files(res_dir: Path) -> List[Path]:
    """Return sorted list of values-<locale>/strings.xml files, ignoring non-locale folders."""
    files: List[Path] = []
    if not res_dir.is_dir():
        return files
    for child in sorted(res_dir.iterdir()):
        if child.is_dir() and LOCALE_DIR_RE.match(child.name):
            strings_file = child / "strings.xml"
            if strings_file.is_file():
                files.append(strings_file)
    return files


def load_lock(lock_path: Path) -> Dict[str, str]:
    if not lock_path.is_file():
        return {}
    try:
        data = json.loads(lock_path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError("root must be a JSON object")
        return {str(k): str(v) for k, v in data.items()}
    except Exception as exc:
        raise ValueError(f"{lock_path}: invalid lock file: {exc}") from exc


def save_lock(lock_path: Path, lock_data: Dict[str, str]) -> None:
    ordered = {k: lock_data[k] for k in sorted(lock_data)}
    lock_path.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def mutate_locale_xml(
    locale_file: Path,
    keys_to_delete: Set[str],
    renames: Dict[str, str],
) -> bool:
    """
    Apply deterministic key deletions and key renames to a locale strings.xml
    while preserving formatting and comments.
    """
    content = locale_file.read_text(encoding="utf-8")
    original = content

    # 1. Delete removed / non-translatable keys (match self-closing or standard <string> block)
    for key in sorted(keys_to_delete):
        pattern = re.compile(
            rf'^[ \t]*<string\s+name="{re.escape(key)}"[^>]*(?:/>|>.*?</string>)[ \t]*\r?\n?',
            re.MULTILINE | re.DOTALL,
        )
        content = pattern.sub("", content)

    # 2. Rename keys in-place
    for old_key, new_key in sorted(renames.items()):
        pattern = re.compile(rf'(<string\s+name="){re.escape(old_key)}(")')
        content = pattern.sub(rf"\g<1>{new_key}\g<2>", content)

    if content != original:
        locale_file.write_text(content, encoding="utf-8")
        return True
    return False


def validate_single_value(
    locale_file: Path,
    key: str,
    en_text: str,
    loc_text: str,
) -> List[str]:
    """Run crash-preventing and semantic-invariant checks on a single translated string."""
    errors: List[str] = []

    if not loc_text.strip():
        errors.append(f"{locale_file} [{key}]: translation is empty")
        return errors

    errors.extend(check_android_escaping(locale_file, key, loc_text))

    en_placeholders = extract_placeholders(en_text)
    loc_placeholders = extract_placeholders(loc_text)
    if en_placeholders != loc_placeholders:
        errors.append(
            f"{locale_file} [{key}]: format placeholders {loc_placeholders} do not match English {en_placeholders}"
        )

    for token in INVARIANT_TOKENS:
        if token in en_text and token not in loc_text:
            errors.append(
                f"{locale_file} [{key}]: missing invariant token '{token}' present in English source"
            )

    if key in COMPACT_NAV_KEYS:
        max_len = COMPACT_NAV_KEYS[key]
        if len(loc_text.strip()) > max_len:
            errors.append(
                f"{locale_file} [{key}]: bottom-nav label length ({len(loc_text.strip())}) exceeds budget ({max_len}): '{loc_text.strip()}'"
            )

    return errors


def emit_error(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::error::{message}", file=sys.stderr)
    else:
        print(f"error: [translations] {message}", file=sys.stderr)


def emit_warning(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::warning::{message}", file=sys.stderr)
    else:
        print(f"warning: [translations] {message}", file=sys.stderr)


def run_local_sync(base_xml: Path, res_dir: Path, lock_path: Path) -> int:
    en_translatable, en_non_translatable = parse_strings_xml(base_xml)
    current_hashes = {k: sha256_text(v) for k, v in en_translatable.items()}
    locale_files = discover_locale_files(res_dir)
    lock_data = load_lock(lock_path)

    if lock_data and locale_files:
        removed_from_en = set(lock_data.keys()) - set(en_translatable.keys())
        added_to_en = set(en_translatable.keys()) - set(lock_data.keys())

        # Detect unambiguous 1-to-1 renames where old hash == new hash
        removed_by_hash: Dict[str, List[str]] = {}
        for k in removed_from_en:
            removed_by_hash.setdefault(lock_data[k], []).append(k)

        added_by_hash: Dict[str, List[str]] = {}
        for k in added_to_en:
            added_by_hash.setdefault(current_hashes[k], []).append(k)

        renames: Dict[str, str] = {}
        for h, old_keys in removed_by_hash.items():
            new_keys = added_by_hash.get(h, [])
            if len(old_keys) == 1 and len(new_keys) == 1:
                renames[old_keys[0]] = new_keys[0]

        keys_to_delete = (removed_from_en - set(renames.keys())) | en_non_translatable

        if renames or keys_to_delete:
            mutated_count = 0
            for lf in locale_files:
                if mutate_locale_xml(lf, keys_to_delete, renames):
                    mutated_count += 1

            # Update lock file for deterministic deletions and renames
            new_lock = dict(lock_data)
            for k in removed_from_en - set(renames.keys()):
                new_lock.pop(k, None)
            for k in en_non_translatable:
                new_lock.pop(k, None)
            for old_k, new_k in renames.items():
                new_lock.pop(old_k, None)
                new_lock[new_k] = current_hashes[new_k]

            if new_lock != lock_data:
                save_lock(lock_path, new_lock)
                lock_data = new_lock

            if renames:
                pairs = ", ".join(f"{o} -> {n}" for o, n in sorted(renames.items()))
                print(f"[translations] Auto-renamed keys across {mutated_count} locale(s): {pairs}")
            if keys_to_delete & removed_from_en:
                deleted_list = ", ".join(sorted(keys_to_delete & removed_from_en))
                print(
                    f"[translations] Auto-deleted removed keys across {mutated_count} locale(s): {deleted_list}"
                )

    # Now validate crash-inducing defects on whatever translations exist
    hard_errors: List[str] = []
    missing_or_stale: Set[str] = set()

    for k, h in current_hashes.items():
        if k not in lock_data or lock_data[k] != h:
            missing_or_stale.add(k)

    for lf in locale_files:
        try:
            loc_map, loc_non_trans = parse_strings_xml(lf)
        except ValueError as exc:
            hard_errors.append(str(exc))
            continue

        leaked_non_trans = (set(loc_map.keys()) | loc_non_trans) & en_non_translatable
        for k in sorted(leaked_non_trans):
            hard_errors.append(
                f"{lf} [{k}]: key is marked translatable=\"false\" in values/strings.xml and must not appear in locale files"
            )

        for k, loc_val in loc_map.items():
            if k in en_translatable:
                hard_errors.extend(validate_single_value(lf, k, en_translatable[k], loc_val))

        for k in en_translatable:
            if k not in loc_map:
                missing_or_stale.add(k)

    if hard_errors:
        for err in hard_errors:
            emit_error(err)
        emit_error(
            f"Localization structural check failed ({len(hard_errors)} error(s)). "
            f"Ask the agent to follow {SKILL_PATH} to fix malformed translations."
        )
        return 1

    if locale_files and missing_or_stale:
        sample = ", ".join(sorted(missing_or_stale)[:8])
        more = (
            f" (+{len(missing_or_stale) - 8} more)"
            if len(missing_or_stale) > 8
            else ""
        )
        emit_warning(
            f"{len(missing_or_stale)} added/modified string(s) need translation [{sample}{more}]. "
            f"Local build will proceed using English fallback. Before pushing, ask the agent to follow {SKILL_PATH}."
        )

    return 0


def run_ci_check(base_xml: Path, res_dir: Path, lock_path: Path) -> int:
    en_translatable, en_non_translatable = parse_strings_xml(base_xml)
    current_hashes = {k: sha256_text(v) for k, v in en_translatable.items()}
    locale_files = discover_locale_files(res_dir)
    errors: List[str] = []

    if not locale_files:
        emit_error(
            f"No values-*/strings.xml files found under {res_dir}. "
            f"Ask the agent to follow {SKILL_PATH}."
        )
        return 1

    if not lock_path.is_file():
        emit_error(
            f"Missing lock file {lock_path}. "
            f"Ask the agent to follow {SKILL_PATH} or run 'python3 scripts/translations.py --update-lock'."
        )
        return 1

    lock_data = load_lock(lock_path)

    # 1. Check lock parity against values/strings.xml
    untracked_keys = sorted(set(en_translatable.keys()) - set(lock_data.keys()))
    stale_keys = sorted(
        k for k in set(en_translatable.keys()) & set(lock_data.keys()) if lock_data[k] != current_hashes[k]
    )
    orphaned_lock_keys = sorted(set(lock_data.keys()) - set(en_translatable.keys()))

    if untracked_keys:
        errors.append(
            f"New English string(s) not yet translated or locked: {', '.join(untracked_keys)}"
        )
    if stale_keys:
        errors.append(
            f"Modified English string(s) whose translations are now stale: {', '.join(stale_keys)}"
        )
    if orphaned_lock_keys:
        errors.append(
            f"Deleted/non-translatable key(s) still present in {lock_path.name}: {', '.join(orphaned_lock_keys)} "
            f"(run 'python3 scripts/translations.py --local-sync' locally)"
        )

    # 2. Check every locale file
    en_key_set = set(en_translatable.keys())
    for lf in locale_files:
        try:
            loc_map, loc_non_trans = parse_strings_xml(lf)
        except ValueError as exc:
            errors.append(str(exc))
            continue

        loc_keys = set(loc_map.keys())
        missing = sorted(en_key_set - loc_keys)
        extra = sorted((loc_keys | loc_non_trans) - en_key_set)

        if missing:
            errors.append(f"{lf}: missing {len(missing)} key(s): {', '.join(missing[:10])}")
        if extra:
            errors.append(
                f"{lf}: unexpected/non-translatable key(s): {', '.join(extra[:10])}"
            )

        for k in sorted(en_key_set & loc_keys):
            errors.extend(validate_single_value(lf, k, en_translatable[k], loc_map[k]))

    if errors:
        for err in errors:
            emit_error(err)
        emit_error(
            f"CI localization verification failed with {len(errors)} issue(s). "
            f"To translate new/modified strings or repair locale files, ask the agent to follow {SKILL_PATH} "
            f"(or run 'python3 scripts/translations.py --local-sync' for key renames/deletions)."
        )
        return 1

    print(
        f"OK: {len(locale_files)} locale(s) and {len(en_translatable)} translatable keys verified."
    )
    return 0


def run_update_lock(base_xml: Path, res_dir: Path, lock_path: Path) -> int:
    en_translatable, en_non_translatable = parse_strings_xml(base_xml)
    locale_files = discover_locale_files(res_dir)

    if not locale_files:
        emit_error(f"Cannot update lock: no values-*/strings.xml files found in {res_dir}")
        return 1

    # Write candidate lock first, then run full CI check; restore previous lock if check fails
    current_hashes = {k: sha256_text(v) for k, v in en_translatable.items()}
    previous = lock_path.read_text(encoding="utf-8") if lock_path.is_file() else None
    save_lock(lock_path, current_hashes)

    rc = run_ci_check(base_xml, res_dir, lock_path)
    if rc == 0:
        print(f"Updated {lock_path} ({len(current_hashes)} keys).")
    elif previous is None:
        lock_path.unlink(missing_ok=True)
    else:
        lock_path.write_text(previous, encoding="utf-8")
    return rc


def main() -> int:
    parser = argparse.ArgumentParser(description="Safe-Box translation sync and verifier.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--local-sync", action="store_true", help="Auto-sync renames/deletes; warn on untranslated")
    group.add_argument("--ci-check", action="store_true", help="Strict read-only check for CI")
    group.add_argument("--update-lock", action="store_true", help="Verify all locales and update app/translations.lock")

    parser.add_argument(
        "--res-dir",
        type=Path,
        default=Path("app/src/main/res"),
        help="Path to Android res/ directory (default: app/src/main/res)",
    )
    parser.add_argument(
        "--lock-file",
        type=Path,
        default=Path("app/translations.lock"),
        help="Path to translations lock file (default: app/translations.lock)",
    )
    parser.add_argument(
        "--stamp-file",
        type=Path,
        default=None,
        help="Optional Gradle stamp output file written when the command succeeds",
    )
    args = parser.parse_args()

    res_dir: Path = args.res_dir
    base_xml: Path = res_dir / "values" / "strings.xml"
    lock_path: Path = args.lock_file

    if not base_xml.is_file():
        emit_error(f"Base strings file not found: {base_xml}")
        return 1

    try:
        if args.local_sync:
            rc = run_local_sync(base_xml, res_dir, lock_path)
        elif args.ci_check:
            rc = run_ci_check(base_xml, res_dir, lock_path)
        elif args.update_lock:
            rc = run_update_lock(base_xml, res_dir, lock_path)
        else:
            rc = 0
    except ValueError as exc:
        emit_error(f"{exc} — see {SKILL_PATH}")
        return 1

    if rc == 0 and args.stamp_file is not None:
        args.stamp_file.parent.mkdir(parents=True, exist_ok=True)
        args.stamp_file.write_text("ok\n", encoding="utf-8")

    return rc


if __name__ == "__main__":
    sys.exit(main())
