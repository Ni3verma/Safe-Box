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
| `minSdk` / `targetSdk` / `compileSdk` | **29** / 37 / 37 on `feature/envelope-encryption` ([ADR-0012](../docs/decisions/0012-min-sdk-29.md), 2026-10-21); `master` stays 24 until the final merge |
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

- `versionCode = (GITHUB_RUN_NUMBER ?: 9999984) + 15` (local builds: `9999999`); `versionName` is `GITHUB_REF_NAME` (local: `LOCAL-build`).
- Tag convention is **`vMAJOR.MINOR.DBVERSION.FIX`** — the third component is the **Room schema
  version**. Enforced on tag push by `release.yml`'s `tag_db_version` job; see the release-and-ci skill.

## Persistence & crypto

- Room DB version: **5** on `master`; **6** on `feature/envelope-encryption` (`MIGRATION_5_6`,
  2026-10-21: `vault_meta` + `quarantined_record`, `user_details` loses `password`, the hash is
  parked as `vault_meta.legacy_password_hash`). Exported schemas `1.json`–`6.json` in
  `app/schemas/`, also wired in as androidTest assets.
- `Migration.ALL` (`data/db/Migration.kt`) is the single migration list: `CacheModule` spreads it into
  `.addMigrations(*Migration.ALL)` and the full-chain migration test reads it too. Add migrations there only.
- **No `fallbackToDestructiveMigration` anywhere.** Verified by grep. Keep it that way.
- Field encryption: AES-GCM per field, `b64(ct)|b64(iv)` (`security/AesGcmFieldCipher.kt`). **The key
  differs by branch.** `master`: a `@Singleton` Keystore key, alias **`symmetricDataKey`**
  (`di/SecurityModule.kt`), whose provider does `if (!containsAlias) generateKey()` — a lost alias
  **silently regenerates** and every record becomes undecryptable (visible since 2026-10-20 as
  `keystore_key_created` with `db_exists = 1`). `feature/envelope-encryption` (MR 3, 2026-10-21): no
  ambient key; `SymmetricKeyUtilsImpl` reads `SessionKeyHolder` per call, a locked vault fails closed
  (`SessionLockedException`; list Flows emit empty); nothing creates the legacy alias any more (MR 5).
  MR 4 (2026-10-23): `clearSessionKey()` is the exit from the vault — `HomeScreen` leaves for Login on
  `isVaultUnlocked = false`; the timeout records `SessionLockReason.AWAY_TIMEOUT` and clears, **nothing
  navigates**. Start destination is Room-first (`VaultRepository.hasVault()`); `IS_SIGN_UP_REQUIRED` is
  written, never read. MR 6 (2026-10-25): every unlock is `LoginViewModel` → `setSessionKey` — password,
  fingerprint (`CryptoObject` cipher prepared by the VM), forgot-password (`wrapped_dek_device`, then
  `rewrapPassword(newPassword, dek, hint)`); `VaultResetUseCase.eraseVault()` is the second clear.
  [Detail](../docs/architecture/persistence-and-crypto.md#field-encryption)
- `BACKUP_VERSION` in `common/CommonConstants.kt`: **3**. Version 3 added
  `AUTHENTICATOR_DATA_KEY = "8"` to the export map; a v2 file simply has no key `"8"`. Changing the
  export classes (`data/db/docs/export/`) fails `scripts/tests/backup-format-test.sh` until
  `BACKUP_VERSION` is bumped, `app/backup-format.lock` gains a line and a new seed is captured.
- **Master password rules** (`ui/core/password/PasswordValidator.kt`, verified 2026-09-21): non-blank,
  mixed case, **≥ 2 digits**, ≥ 1 non-alphanumeric, length **≥ 7**. Signup also needs a non-blank
  hint. Applies to signup and change-password only — **not** to the backup file password, which is
  unconstrained. Any **vault master password** you invent for a test or fixture must satisfy all
  five; backup-file passwords need not.

Full detail: [docs/architecture/persistence-and-crypto.md](../docs/architecture/persistence-and-crypto.md)

## Backup & restore

- Backup file `yyyyMMddHHmmssSSS.bak` goes into a directory picked **once** via `OpenDocumentTree`
  (mime `application/octet-stream`); restore uses `OpenDocument` with mimes `application/octet-stream`,
  `application/x-trash`, `application/x-binary`.
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
- **To read a `.bak` without an emulator**, run `scripts/InspectBackup.java`; it carries the PBE parameters
  (`PBKDF2WithHmacSHA1`, 1324 iterations, `AES/CBC/PKCS5Padding`) in executable form. Verified 2026-09-21.
- `ObjectInputStream.resolveClass` is allowlisted in `RestoreDataWorker` — **do not widen it**, and
  do not translate it into an `ObjectInputFilter`; the two allowlists see different class sets. Both
  forms: [persistence-and-crypto.md](../docs/architecture/persistence-and-crypto.md).
- **Restore is a destructive replace**, not a merge: `restoreDataToDb` calls `deleteAllData()` per
  table inside `runInTransaction`. Post-restore counts equal the file's counts exactly **for every
  type except authenticator**: `filterDecodableAuthenticatorData` **silently drops** records failing
  `totpGenerator.isValidConfig(...)` (firing `RESTORE_INVALID_AUTHENTICATOR_SKIPPED` with a count).
  Assert `<=` for authenticators, `==` for the rest.
- Worker failure enums are append-only, ordinals pinned by `*FailureReasonTest`. Restore: `BACKUP_TOO_NEW`,
  `BACKUP_EMPTY` (crafted files only; an empty vault writes no file), etc. Backup (#274): only
  `FOLDER_INACCESSIBLE` clears the saved folder. `feature/envelope-encryption` (MR 4, 2026-10-23): both end
  in `SESSION_LOCKED`; the backup password reaches a worker via `BackupPasswordRegistry` (process memory, keyed
  by work id), **never** WorkManager input data; a lock cancels nothing — the worker fails closed at its next use of the key. [doc](../docs/architecture/persistence-and-crypto.md)

## Testing

- Unit tests: `:app:testDebugUnitTest`. Instrumentation: **debug build only**
  ([ADR-0001](../docs/decisions/0001-instrumentation-tests-run-on-debug-only.md)); runner
  `com.andryoga.safebox.CustomHiltTestRunner`, test Application `BaseTestApplication`.
- **`Modifier.testTag` only where a label cannot identify the element** (`ui/core/TestTags.kt`),
  exposed as resource ids in `debug`/`qa` only; everything else by text or content description.
- The `aosp-atd` CI image has **no DocumentsUI** (so no `:upgrade-test`, 2026-09-23) and **no secure lock
  screen** (no auth-bound Keystore keys; `locksettings` is ignored — [ADR-0011](../docs/decisions/0011-envelope-encryption-spike-results.md) S8, 2026-10-21).
- **`:upgrade-test`**: self-instrumenting `com.android.test` module for the black-box upgrade and restore
  tests on the QA APK; `assembleDebug` runs **zero `:app` tasks** (2026-09-22). Driven by
  `scripts/run-{upgrade,restore}-test.sh`; gates `release.yml`. [Doc](../docs/testing/upgrade-testing.md)
- In-app updates are `release`-only (`IN_APP_UPDATE_ENABLED`, 2026-09-28); every androidTest gets
  Play's `FakeAppUpdateManager` via `FakeAppUpdateModule`. [Design](../docs/architecture/in-app-updates.md)

Procedures and gotchas: [skills/build-and-test/SKILL.md](skills/build-and-test/SKILL.md)

## CI & releases

- `release.yml` publishes **`SafeBox-qa.apk` and `app-release.aab` as GitHub Release assets** on
  every tag (old QA APKs are archived back to at least `v2.0.4.0`), then uploads that AAB to Play's
  **internal, closed + open testing** tracks (`release_on_play`, ADR-0009); production is a manual promotion.
  Notes: `distribution/whatsnew/<MAJOR.MINOR.DB.FIX>/whatsnew-<locale>`, absent = none. (2026-10-07)
- The QA signing certificate has been **stable** from `v1.4.4.0` to local builds; its SHA-256 is pinned
  as `QA_CERT_SHA256` in `scripts/lib/harness.sh` and checked on every upgrade/restore run. Verified 2026-09-26.
- APK output names: `SafeBox-<variant>.apk` (`androidComponents` in `app/build.gradle`); four workflow paths hard-code them. Verified 2026-10-04.
- **Store listing images are generated, never edited**: `screenshots/readme/NN-*.png` and the README
  grid between the `store-screenshots` markers come from `scripts/take-store-screenshots.sh`; edit
  `scenes.json`/`theme.json` and re-render. [Runbook](skills/store-screenshots/SKILL.md). (2026-10-04)

Detail: [skills/release-and-ci/SKILL.md](skills/release-and-ci/SKILL.md)

## Known traps

- An authenticator record whose Base32 seed is invalid **cannot be repaired in the UI** (the seed field
  is `visibleIn = setOf(ViewMode.NEW)`, so EDIT never renders it); delete and re-add is the only recourse.
- The user has an explicit standing preference for **few logs**, because they reach production
  builds. Do not add `Timber` calls casually.
- Firebase's automatic `screen_view` is **one screen for the whole app** (single Activity), so every
  funnel step must be an explicit `AnalyticsKey`: [activation-funnel.md](../docs/analytics/activation-funnel.md). (2026-10-17)
