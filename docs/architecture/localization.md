# Localization Architecture

Safe-Box ships localized UI strings across **26 LTR locales** alongside the default English [`app/src/main/res/values/strings.xml`](../../app/src/main/res/values/strings.xml). Step-by-step commands and glossary rules live in the [localization skill](../../.agents/skills/localization/SKILL.md).

## Design Goals

1. **Zero friction during local feature development:** Adding or editing an English string in `values/strings.xml` must never block local APK builds (`assembleDebug`, `assembleQa`). Android falls back to the English string at runtime until translations are generated.
2. **Deterministic changes require no AI agent:** Deleting a key, renaming a key (with unchanged English text), or marking a key `translatable="false"` is propagated automatically across all 26 locale files during local Gradle builds (`syncTranslations` wired into `preBuild`).
3. **Strict CI gate on PRs:** No PR can merge with untranslated strings, stale translations, broken format placeholders, unescaped quotes, or corrupted brand/cryptographic identifiers.

## Why `app/translations.lock` Exists (The Stale-Translation Trap)

Android's built-in `MissingTranslation` and `ExtraTranslation` Lint checks only inspect **key presence**. If an existing English string's wording changes (for example, updating a security warning or changing a numeric limit in prose), every `values-<locale>/strings.xml` file still contains that key—so Android Lint reports zero issues while 26 locales display outdated or contradictory instructions.

[`app/translations.lock`](../../app/translations.lock) maps every translatable `<string name="...">` key in `values/strings.xml` to the 16-character SHA-256 prefix of its English text:

- **Stale detection in CI:** When an English string's text changes, its hash in `values/strings.xml` diverges from `app/translations.lock`, and `scripts/translations.py --ci-check` fails the PR with instructions to run the [localization skill](../../.agents/skills/localization/SKILL.md).
- **Automatic 1-to-1 rename detection locally:** When `old_key` disappears from `values/strings.xml` and `new_key` appears with the exact same English text hash that `old_key` held in `app/translations.lock`, `scripts/translations.py --local-sync` renames `name="old_key"` → `name="new_key"` across all 26 `values-*/strings.xml` files and updates the lock file automatically.

## Build & CI Split

| Layer | Mode | Behaviour |
|---|---|---|
| Local Gradle (`syncTranslations` before `preBuild`, skipped when `CI` is set) | `scripts/translations.py --local-sync` | Auto-applies key renames, deletions, and `translatable="false"` removals. Warns (exit 0) when new/modified English strings await translation. Fails only on crash-inducing defects (broken XML, unescaped apostrophes, mismatched `%1$s`/`%1$d`, or missing invariant tokens). |
| Android Lint (`app/build.gradle`) | `warning 'MissingTranslation'` | Downgrades `MissingTranslation` from `Fatal` to `warning` so `lintVitalAnalyzeQa` never blocks local QA builds during active development. |
| GitHub Actions (`.github/workflows/ci.yml`) | `./scripts/tests/translations-test.sh` (`--ci-check`) | Read-only gate before JDK/Gradle setup. Fails on any missing translation, stale English text hash, extra/non-translatable key, placeholder mismatch, invariant token loss, or nav-bar length overflow. |

## Android Resource Conventions

- **Per-app language preferences (Android 13+):** `androidResources { generateLocaleConfig = true }` in [`app/build.gradle`](../../app/build.gradle) coupled with [`app/src/main/res/resources.properties`](../../app/src/main/res/resources.properties) (`unqualifiedResLocale=en-US`) generates the locale list automatically so Android system settings allow choosing Safe-Box's language independently.
- **Indonesian qualifier (`values-in`):** Android `Resources` on legacy API levels (`minSdk 24`+) resolves Indonesian via the legacy ISO-639 code `in` (`Locale("id").language == "in"` on older Android runtimes), while AGP's `generateLocaleConfig` maps it cleanly for Android 13+.
- **Non-translatable identifiers (`translatable="false"`):** `app_name`, `notification_backup_channel_id`, `url`, `cvv`, and `safe_box_feedback` are marked `translatable="false"`. In particular, `notification_backup_channel_id` is passed to `NotificationChannel(...)` in [`Utils.makeStatusNotification`](../../app/src/main/java/com/andryoga/safebox/common/Utils.kt); translating it would create orphaned duplicate notification channels whenever a user switches device language.
