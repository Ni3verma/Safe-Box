# Safe-Box — Verified Project Facts

Short, high-value invariants about **this** repository. Read this before starting any non-trivial
task so you do not re-derive things that are already known.

**Rules for this file**

- Every entry must be **verifiable** — state how it was checked, and when.
- Keep it under ~150 lines — it is read on every task, so adding a fact means evicting one; the
  eviction order is in [AGENTS.md](AGENTS.md#curate-do-not-accumulate). Facts only: behavioural
  rules go in AGENTS.md, procedures in `skills/`, long-form reasoning in `docs/`.
- If a fact turns out to be wrong, **fix it in place** rather than adding a contradicting entry.
- **Facts here describe `master` unless a branch is named.** Run `git branch --show-current` before
  trusting anything version-specific, and record both values for any fact that differs across
  branches — writing a feature-branch fact as universal has already misled once (`BACKUP_VERSION`).

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
  version**. `v2.0.4.0` shipped DB v4; the schema is now **5**, so the next release must be
  `v2.x.5.0`, not `v2.1.4.x`. (Verified 2026-09-20.)

## Persistence & crypto

- Room DB version: **5** (`data/db/SafeBoxDatabase.kt`). Exported schemas `1.json`–`5.json` in
  `app/schemas/`, also wired in as androidTest assets.
- `Migration.ALL` in `data/db/Migration.kt` is the single source of truth for the migration list;
  `CacheModule` spreads it into `.addMigrations(*Migration.ALL)`. Add a migration there and it is
  wired into both production and the full-chain migration test.
- **No `fallbackToDestructiveMigration` anywhere.** Verified by grep. Keep it that way.
- Field encryption: AES-GCM key in `AndroidKeyStore` under alias **`symmetricDataKey`**
  (`di/SecurityModule.kt`). The provider does `if (!keyStore.containsAlias(alias)) generateKey()`,
  so a lost alias **silently regenerates** and every stored record becomes undecryptable with no
  error. This is the single highest-severity failure mode in the app.
- `BACKUP_VERSION` in `common/CommonConstants.kt`: **3**. Version 3 added
  `AUTHENTICATOR_DATA_KEY = "8"` to the export map; a v2 file simply has no key `"8"`, which is what
  makes `upgrade-test/src/main/assets/fixtures/v2_pre_totp.bak` a valid pre-TOTP fixture.
- Record types (`domain/models/record/RecordType.kt`): `LOGIN`, `CARD`, `BANK_ACCOUNT`, `NOTE`,
  `AUTHENTICATOR`.
- **Master password rules** (`ui/core/password/PasswordValidator.kt`, verified 2026-09-21): non-blank,
  mixed case, **≥ 2 digits**, ≥ 1 non-alphanumeric, length **≥ 7**. Signup also needs a non-blank
  hint. Applies to signup and change-password only — **not** to the backup file password, which is
  unconstrained. Any **vault master password** you invent for a test or fixture must satisfy all
  five; backup-file passwords need not.

Full detail: [docs/architecture/persistence-and-crypto.md](../docs/architecture/persistence-and-crypto.md)

## Backup & restore

- Backup file: `yyyyMMddHHmmssSSS.bak`, written into a directory the user picks **once** via
  `OpenDocumentTree`, created as mime `application/octet-stream`.
- Restore: `OpenDocument` with mimes `application/octet-stream`, `application/x-trash`,
  `application/x-binary`.
- Payload is a Java-serialized **`LinkedHashMap<String, ByteArray?>`** (Kotlin's `mutableMapOf()`),
  PBE-encrypted with a **user-supplied backup password** independent of the vault master password.
  Keys `"0"`–`"8"`: version, salt, IV, creation date, then one per record type (`4` login,
  `5` bank account, `6` bank card, `7` secure note, `8` authenticator).
- **Absent key and null value are different signals.** `encrypt*Data` returns `null` for a record
  type with no rows, so the key is present holding null. Absent means the file predates the key;
  null means the type is supported and the vault had none. Conflating them reads a v3 backup with
  no TOTP records as a pre-TOTP v2 file.
- Key `"3"` is **raw big-endian bytes**, not text — 8 bytes since v2, **1 byte in v1**, and both are
  still read. Decoding it as UTF-8 prints control characters and looks like corruption.
- **To read a `.bak` without an emulator**, run `scripts/InspectBackup.java` — it also carries the
  PBE parameters (`PBKDF2WithHmacSHA1`, 1324 iterations, `AES/CBC/PKCS5Padding`) in executable form,
  so read them there rather than copying them around. Verified 2026-09-21.
- `ObjectInputStream.resolveClass` is allowlisted in `RestoreDataWorker` — **do not widen it**, and
  do not translate it into an `ObjectInputFilter`; the two allowlists see different class sets. Both
  forms: [persistence-and-crypto.md](../docs/architecture/persistence-and-crypto.md).
- **Restore is a destructive replace**, not a merge: `restoreDataToDb` calls `deleteAllData()` per
  table inside `runInTransaction`. Post-restore counts equal the file's counts exactly **for every
  type except authenticator**. `filterDecodableAuthenticatorData` partitions authenticator records
  on `totpGenerator.isValidConfig(...)` and **silently drops the invalid ones** (logging a count and
  firing `RESTORE_INVALID_AUTHENTICATOR_SKIPPED`), so a file containing a malformed seed restores
  fewer authenticator rows than it holds. Assert `<=` for authenticators, `==` for the rest.
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
  required. `aosp-atd` suffices for `:app`'s own UI tests but **not** `:upgrade-test` (no DocumentsUI).
- **`:upgrade-test`** is a self-instrumenting `com.android.test` module for black-box upgrade
  testing. `:upgrade-test:assembleDebug` runs **zero `:app` tasks** (verified 2026-09-22), so R8
  cannot break it. Gradle only assembles it — `scripts/run-upgrade-test.sh` installs and drives it.
  Running and extending it: [docs/testing/upgrade-harness-operations.md](../docs/testing/upgrade-harness-operations.md)

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
- The user has an explicit standing preference for **few logs**, because they reach production
  builds. Do not add `Timber` calls casually.
- **`CICD/cicd.gradle` is orphaned** since `331ee64` — nothing applies it. So `./gradlew detekt`
  fails with "Task 'detekt' not found" (detekt is `apply false` at the root and never applied to
  `:app`, leaving `CICD/detekt.yml` not in effect), and the `copyGitHooks` / `installGitHooks` tasks
  do not exist, so git hooks must be installed by hand and drift silently. Install and drift check:
  [skills/build-and-test/SKILL.md](skills/build-and-test/SKILL.md#git-hooks). Verified 2026-09-21.
- Adding any new Android module: a subproject can only `alias(libs.plugins.android.*)` if the
  **root `build.gradle` also declares it `apply false`**. Otherwise Gradle fails with "the plugin is
  already on the classpath with an unknown version", because AGP arrives a second time via the root
  `buildscript` classpath. (Verified 2026-09-22.)
