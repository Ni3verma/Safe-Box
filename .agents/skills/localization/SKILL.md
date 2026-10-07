---
name: localization
description: How Safe-Box strings.xml is translated across 26 locales, how local Gradle auto-sync handles key renames and deletions, and the exact workflow for translating new or updated English strings
---

# Localization (`strings.xml`)

English [`app/src/main/res/values/strings.xml`](../../../app/src/main/res/values/strings.xml) is the single source of truth. Its last-translated snapshot is recorded in [`app/translations.lock`](../../../app/translations.lock) (each translatable key mapped to the SHA-256 prefix of its English text).

## Supported Locales (26 LTR Locales)

| Region | Resource Directories (`app/src/main/res/values-<qualifier>/strings.xml`) |
|---|---|
| Europe (West/North/South) | `es`, `fr`, `de`, `it`, `pt-rBR`, `pt-rPT`, `nl`, `sv` |
| Europe (Central/East) | `pl`, `ru`, `uk`, `cs`, `ro`, `el`, `tr` |
| East & Southeast Asia | `ja`, `ko`, `zh-rCN`, `zh-rTW`, `in` (Indonesian), `vi`, `th` |
| Indic | `hi`, `bn`, `ta`, `mr` |

> [!NOTE]
> Indonesian uses `values-in` so Android `Resources` resolves it across the full `minSdk 24`–`targetSdk 37` range (`Locale("id").language` returns `"in"` on legacy Android runtimes).

## Commands

| Goal | Command |
|---|---|
| Auto-apply key renames, deletions & `translatable="false"` removals (runs automatically on local Gradle `preBuild`) | `python3 scripts/translations.py --local-sync` |
| Strict read-only verification (runs in CI on every PR) | `python3 scripts/translations.py --ci-check` |
| Update `app/translations.lock` after translating new/updated keys | `python3 scripts/translations.py --update-lock` |
| Run the script's own test suite + repo check | `./scripts/tests/translations-test.sh` |

## Workflow When Adding or Editing Strings

### 1. Deterministic Changes (No Agent Translation Needed)
- **Deleting a key**, **renaming a key** (keeping its English text unchanged), or **marking a key `translatable="false"`** in `values/strings.xml` is handled automatically by `python3 scripts/translations.py --local-sync` (which also runs on every local `./gradlew assembleDebug` / `assembleQa`).
- Do **not** invoke AI translation for pure key renames or deletions.

### 2. Adding New Strings or Changing English Wording
When `python3 scripts/translations.py --ci-check` (or the local build warning) reports untranslated or modified keys:

1. **Identify only the delta keys:**
   Run `python3 scripts/translations.py --ci-check` to list the exact keys that are missing or stale relative to `app/translations.lock`. Never re-translate untouched keys.
2. **Apply the Context & Glossary Rules (below)** to translate **only** those keys into all 26 `app/src/main/res/values-*/strings.xml` files.
3. **Follow Android XML escaping & placeholder rules:**
   - Preserve positional placeholders (`%1$s`, `%1$d`) identically.
   - Escape apostrophes as `\'` (especially in `fr`, `it`, `nl`, `pt-*`, `ro`) unless the entire string value is enclosed in `"..."`.
   - Escape `&` as `&amp;` and `>` as `&gt;`.
   - Preserve `\n` line breaks.
4. **Update the lock & verify:**
   ```bash
   python3 scripts/translations.py --update-lock
   ./scripts/tests/translations-test.sh
   ```
   `--update-lock` writes `app/translations.lock` and runs `--ci-check`; it exits non-zero if any locale is missing a key, has an extra/non-translatable key, or violates placeholder/token/escaping rules.

## Translation Glossary & Disambiguation Rules

### Invariant Tokens (Never Translate or Alter Casing)
Whenever any of these tokens appear in the English source string, they **must** appear verbatim in the translated string (`scripts/translations.py` enforces this):
- `Safe Box`, `SafeBox`
- `GitHub`
- `Base32`, `SHA1`, `SHA256`, `SHA512`, `TOTP`
- `IFSC`, `MICR`

Strings marked `translatable="false"` (`app_name`, `notification_backup_channel_id`, `url`, `cvv`, `safe_box_feedback`) must **never** appear in any `values-*/strings.xml`.

### Semantic Disambiguation
- **`type_display_card`**: Bank payment card (credit/debit card), **not** a UI card or playing/greeting card.
- **`type_display_account`**: Bank account, **not** a user profile account.
- **`type_display_note` / `notes`**: Secure text note / memo.
- **`type_display_login` vs `login`**: `type_display_login` is a noun (a saved login credential record); `login` is an action button ("Log in" / "Sign in").
- **`pin`**: Numeric security/card PIN (keep `PIN` or standard local equivalent such as `Code PIN`, `PIN-код`, `Mã PIN`).
- **`cd_*` keys**: TalkBack accessibility content descriptions. Use natural platform screen-reader phrasing.

### Compact UI Length Budgets
- **`bottom_nav_records`** (≤ 24 chars), **`bottom_nav_backup_and_restore`** (≤ 28 chars), **`bottom_nav_settings`** (≤ 24 chars): Enforced by `scripts/translations.py` so `NavigationBarItem` labels do not clip or wrap across multiple lines.
- **Dialog buttons** (`common_cancel`, `confirm`, `common_ok`, `allow`, `retry`, `close`, `save`): Use concise single-word or standard OS imperative labels.
