# Safe-Box — Verified Project Facts

Short, high-value invariants about **this** repository. Read this before starting any non-trivial
task so you do not re-derive things that are already known.

**Rules for this file**

- Every entry must be **verifiable** — state how it was checked, and when.
- Keep it under ~150 lines. It is read on every task; length costs tokens on every request.
- Facts only. Behavioural rules go in [AGENTS.md](AGENTS.md). Procedures go in `skills/`.
  Long-form reasoning goes in `docs/`.
- If a fact turns out to be wrong, **fix it in place** rather than adding a contradicting entry.

---

## Identity & build types

| | |
|---|---|
| Namespace | `com.andryoga.safebox` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 37 / 37 |
| JDK / Kotlin toolchain | 17 |
| NDK | `29.0.14206865` (CI has a gatekeeper step that fails if missing) |
| Build system | Groovy `build.gradle` — **never** Kotlin DSL |
| Dependencies | version catalog at `gradle/libs.versions.toml` |

| Build type | applicationId | R8 | Signing |
|---|---|---|---|
| `debug` | `com.andryoga.safebox.debug` | off | debug key |
| `qa` | `com.andryoga.safebox.qa` | **on** (`initWith(release)`, `shrinkResources`) | `nonProdReleaseKeyStore.properties` |
| `release` | `com.andryoga.safebox` | **on** | `releaseKeyStore.properties` |

Three different `applicationId`s means **no build type can ever upgrade into another**. Any
upgrade test must be `qa → qa`.

## Versioning

- `versionCode = (GITHUB_RUN_NUMBER ?: 9999984) + 15`. Local builds therefore get `9999999`.
- `versionName` comes from `GITHUB_REF_NAME`; local builds are `LOCAL-build`.
- Tag convention is **`vMAJOR.MINOR.DBVERSION.FIX`** — the third component is the **Room schema
  version**. `v2.0.4.0` shipped DB v4.

> [!IMPORTANT]
> The Room schema is now **version 5**. Per the tag convention the next release must be
> `v2.x.5.0`, not `v2.1.4.x`. Verified: `SafeBoxDatabase.kt` `version = 5`, and
> `app/schemas/...SafeBoxDatabase/` contains `1.json`–`5.json`. (2026-09-20)

## Persistence & crypto

- Room DB version **5**; exported schemas in `app/schemas/`, also wired in as androidTest assets.
- `Migration.ALL` in `data/db/Migration.kt` is the single source of truth for the migration list;
  `CacheModule` spreads it into `.addMigrations(*Migration.ALL)`. Keep both in sync via that array.
- **No `fallbackToDestructiveMigration` anywhere.** Verified by grep. Keep it that way.
- Field encryption: AES-GCM key in `AndroidKeyStore` under alias **`symmetricDataKey`**
  (`di/SecurityModule.kt`). The provider does `if (!keyStore.containsAlias(alias)) generateKey()`,
  so a lost alias **silently regenerates** and every stored record becomes undecryptable with no
  error. This is the single highest-severity failure mode in the app.
- `BACKUP_VERSION = 3` in `common/CommonConstants.kt`.
- Record types (`domain/models/record/RecordType.kt`): `LOGIN`, `CARD`, `BANK_ACCOUNT`, `NOTE`,
  `AUTHENTICATOR`.
- **Master password rules** (`ui/core/password/PasswordValidator.kt`, verified 2026-09-21): non-blank,
  mixed case, **≥ 2 digits**, ≥ 1 non-alphanumeric, length **≥ 7**. Signup also needs a non-blank
  hint. Applies to signup and change-password only — **not** to the backup file password, which is
  unconstrained. Any password you invent for a test or fixture must satisfy all five.

Full detail: [docs/architecture/persistence-and-crypto.md](../docs/architecture/persistence-and-crypto.md)

## Backup & restore

- Backup file: `yyyyMMddHHmmssSSS.bak`, written into a directory the user picks **once** via
  `OpenDocumentTree`, created as mime `application/octet-stream`.
- Restore: `OpenDocument` with mimes `application/octet-stream`, `application/x-trash`,
  `application/x-binary`.
- Payload is a Java-serialized `HashMap<String, ByteArray>`, PBE-encrypted with a **user-supplied
  backup password** that is independent of the vault master password.
- `ObjectInputStream.resolveClass` is allowlisted — do not widen it.
- **Restore is a destructive replace**, not a merge: `restoreDataToDb` calls `deleteAllData()` per
  table inside `runInTransaction`. Post-restore counts equal the file's counts exactly.
- `RestoreFailureReason`: `INCORRECT_PASSWORD`, `CORRUPT_OR_INVALID_FILE`, `UNKNOWN_ERROR`.

## Testing

- Unit tests: `app/src/test` — run with `:app:testDebugUnitTest`.
- Instrumentation: `app/src/androidTest`, ~199 tests across ~29 classes, **debug build only**
  (see [ADR-0001](../docs/decisions/0001-instrumentation-tests-run-on-debug-only.md)).
- Runner is `com.andryoga.safebox.CustomHiltTestRunner`; test Application is `BaseTestApplication`.
- Gradle Managed Device: `pixel8Api34` (Pixel 8, API 34, `aosp-atd`).
- **Production code contains zero `Modifier.testTag`.** The suite selects purely by text
  (449 × `onNodeWithText`) and content description (101 × `onNodeWithContentDescription`).
  Verified 2026-09-20.
- ML Kit is `com.google.mlkit:barcode-scanning` (**bundled** model), so no Google Play services are
  required and `aosp-atd` images are sufficient.

Procedures and gotchas: [skills/build-and-test/SKILL.md](skills/build-and-test/SKILL.md)

## CI & releases

- Workflows: `ci.yml`, `nightly.yml` (02:00 UTC, 3 shards), `release.yml` (on `v*` tags),
  `run-ui-test.yml`, `gemini-pr-review.yml`.
- `release.yml` publishes **`SafeBox-qa.apk` and `app-release.aab` as GitHub Release assets** on
  every tag. Old QA APKs are therefore already archived back to at least `v2.0.4.0` — no extra
  archiving step is needed.
- The QA signing certificate has been **stable** across `v2.0.4.0` → `v2.1.4.0-rc3` → local:
  SHA-256 `257ab2043588f0b355bba6a9c9f199c088f079f6306536cd4c94fc2eba7b113d`. Verified 2026-09-20.
- APK output names are set in `androidComponents` as `SafeBox-<variant>.apk`. **Changing that
  breaks the workflow file paths.**

Detail: [skills/release-and-ci/SKILL.md](skills/release-and-ci/SKILL.md)

## Known traps

- An authenticator record whose Base32 seed is invalid **cannot be repaired in the UI** — the seed
  field is `visibleIn = setOf(ViewMode.NEW)`, so it is not rendered in EDIT. Delete and re-add is
  the only recourse.
- Restore silently drops authenticator records that cannot produce a code
  (`filterDecodableAuthenticatorData`). That is intentional — one bad 2FA seed must not cost the
  user every login, card and note in the backup.
- The user has an explicit standing preference for **few logs**, because they reach production
  builds. Do not add `Timber` calls casually.
