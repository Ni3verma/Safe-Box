# Persistence and cryptography

How Safe-Box stores data, what encrypts what, and where the sharp edges are.

## Storage layers

There are **six** independent persistence layers. Most bugs that destroy user data involve one of
them getting out of step with the others, which is why a Room migration test alone is never
sufficient coverage.

| Layer | Holds |
|---|---|
| Room (`SafeBoxDatabase`) | all vault records, per-field encrypted; `user_details` (`uid` in plaintext; the hint encrypted with the records' key while `crypto_version = 1`, `UserDetailsDaoSecure.getHint`); `vault_meta` (the vault's crypto state: `crypto_version`, the parked legacy password hash, later the wrapped keys); `quarantined_record` (rows the crypto migration could not read, see [Room](#room)) |
| `EncryptedSharedPreferences` | signup state, other secrets |
| Plain `SharedPreferences` | login counters, permission-asked flags |
| DataStore | settings/preferences |
| `AndroidKeyStore` | the field-encryption key — **not** file-backed, not in `/data/data` |
| WorkManager's own DB | scheduled backup and clipboard-clear work |

## Field encryption

Encryption is per-field, applied in the `secureDao` layer (`data/db/secureDao/*DaoSecure.kt`), so
DAOs above it deal in plaintext. The on-disk form of every encrypted column is
`b64(ciphertext)|b64(iv)`, AES-256-GCM with a 128-bit tag, produced by the pure codec
`security/AesGcmFieldCipher.kt` over an explicit `SecretKey`.

**On `feature/envelope-encryption` (since envelope MR 3, 2026-10-21) there is no ambient key.**
`SymmetricKeyUtilsImpl` asks `SessionKeyHolder.withSessionKey` for the key on every call — the DEK
once a vault is envelope-encrypted, or the legacy Keystore handle in a legacy session — and a
locked vault fails closed with `SessionLockedException` on both directions:

- the five record `getAll*` Flows catch it through `emptyWhenSessionLocked()` (`common/Utils.kt`) and emit an empty list
  (any other failure still propagates: a broken row is a bug to surface, not a state to hide);
- suspend reads and writes throw it to the caller that owns the recovery: a ViewModel launch that
  touches records goes through `Utils.launchVaultOperation` (since envelope MR 4), which ends the
  operation silently on `SessionLockedException` and propagates anything else — the four
  `SingleRecordViewModel` sites today; the workers return `SESSION_LOCKED` (below);
- the route to Login is central: `HomeScreen` collects `MainViewModel.isVaultUnlocked`
  (`SessionKeyHolder.isUnlocked`, a `StateFlow`) and leaves for the Login graph, composing
  nothing, whenever it is `false` — on entry for a back stack restored after process death or
  a navigation that skipped Login, and the moment the holder is cleared while Home is showing.
  Whoever clears the key gets the navigation for free; nothing else navigates on a lock.

**Who clears the key (since envelope MR 4).** The away timeout: `ActiveSessionManager`, after the
delay, records `SessionLockReason.AWAY_TIMEOUT` in `SessionLockReasonStore` (`ui/core`, process
memory, read-and-cleared once by the next `LoginViewModel`, which drives the "You were away"
banner), then calls `clearSessionKey()`, then logs `SESSION_TIMEOUT{worker_running}` (read just
before the clear) — only when the holder was open, so a timeout on the login screen records and
logs nothing. Every clear emits `lockEvents`, which
`SessionLockWorkCanceller` (`worker/`, started by `MainApplication`) answers by cancelling the
backup and restore work and dropping their passwords, and which `InAppUpdateViewModel` treats as
"locked". There is no `logoutEvent` any more and no away dialog. The start destination is Room's
decision too: `VaultRepository.hasVault()` (a `vault_meta.crypto_version` row, or a `user_details`
row) routes to Login, nothing routes to Signup; `IS_SIGN_UP_REQUIRED` is written but never read for
routing.

The legacy `symmetricDataKey` alias is reached only through `KeystoreGateway`:
`legacyVaultKey()` returns the handle or `null` and **never generates**; the single creation path
is `getOrCreateLegacyVaultKey()`, called by the signup bridge while a vault is being created
(alias-guarded, so an existing entry is never replaced). The hint of a `crypto_version = 1` vault
goes through that handle directly on read and write (`UserDetailsDaoSecure`), because it is the
one field touched outside a session: the login screen reads it, signup writes it before a session
exists, the login-screen reset rewrites it while locked. A lost key reads as `null` and refuses to
write; the login that follows ends in `UserAuthState.LEGACY_KEY_LOST` and nothing is regenerated.
Until MR 5, `LoginViewModel` and `SignupViewModel` open the legacy session (`setLegacySession`)
on every accepted credential; the E2E seeds do the same through `VaultSessionTestBridge`. Design:
[envelope-encryption.md](../design/envelope-encryption.md) §3.1, §4.3, §6.9.

### What `master` still does, and why the branch is shaped like this

`di/SecurityModule.kt` on `master`:

```kotlin
private fun getSymmetricKey(): SecretKey {
    val alias = "symmetricDataKey"
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    if (!keyStore.containsAlias(alias)) {
        // ... AES / GCM / NoPadding / setRandomizedEncryptionRequired(true)
        keyGenerator.generateKey()
    }
    return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
}
```

> [!CAUTION]
> **The highest-severity failure mode in the app, on `master`.** If the alias is ever lost, this
> silently creates a *new* key. Nothing throws *at key creation*. Every existing record becomes
> permanently undecryptable. Observed with the alias deliberately deleted (2026-09-24, upgrade
> harness): the app launched normally and then crashed with `javax.crypto.AEADBadTagException` from
> `AndroidKeyStoreCipherSpiBase.engineDoFinal` at the first decrypt, which was the unlock screen's
> Show Hint, since the hint is encrypted under the same key. Since 2026-10-20 it is at least
> visible there: `keystore_key_created` with `db_exists = 1`
> ([envelope-readiness.md](../analytics/envelope-readiness.md)). On the feature branch the path
> does not exist; `AndroidKeystoreGatewayLegacyKeyTest` pins that a regenerated key fails the GCM
> tag instead of reading garbage.
>
> Because the key lives in the Keystore and not in `/data/data`, it is invisible to Room migration
> tests, to backup/restore tests, and to any `/data` snapshot. The only thing that can catch a
> regression here is an **upgrade test that installs an old build, writes records, upgrades in
> place, and compares decrypted field values**. See
> [docs/testing/upgrade-testing.md](../testing/upgrade-testing.md).

## Backup file format

Produced by `BackupDataWorker`, consumed by `RestoreDataWorker`.

- **Container:** a Java-serialized `LinkedHashMap<String, ByteArray?>`. `BackupDataWorker` builds it
  with Kotlin's `mutableMapOf()`, so `java.util.LinkedHashMap` — not `HashMap` — is the class
  actually written to the file.

  > [!IMPORTANT]
  > The value type is **nullable, and the null is meaningful.** Each `encrypt*Data` helper returns
  > `null` when that record type has no rows, so the key is written **present with a null value**.
  > An *absent* key means the file predates that key; a *null* value means the type is supported and
  > the vault simply had none. Conflating them misreads a v3 backup with no TOTP records as a
  > pre-TOTP v2 file — exactly the distinction `upgrade-test/fixtures/format-2.bak` exists to capture.
- **Keys** are terse numeric strings from `CommonConstants`:

  | Key | Contents |
  |---|---|
  | `"0"` | `BACKUP_VERSION` (currently **3**) |
  | `"1"` | PBE salt |
  | `"2"` | cipher IV |
  | `"3"` | creation date |
  | `"4"`–`"8"` | login / bank account / bank card / secure note / authenticator payloads |

- **Encryption:** payloads are PBE-encrypted with a **backup password supplied by the user at
  export time**. This is *not* the vault master password and *not* the Keystore key — which is why
  a `.bak` remains restorable even if the Keystore key is lost.
- **File name:** `yyyyMMddHHmmssSSS.bak`, mime `application/octet-stream`.
- **Rotation:** `MAX_BACKUP_FILES = 5` — the worker prunes older `.bak` files in the target
  directory.
- **Parameters** (`security/PasswordBasedEncryptionImpl.kt`): `PBKDF2WithHmacSHA1`, **1324**
  iterations, 256-bit key, `AES/CBC/PKCS5Padding`, a **256-byte salt** and a **16-byte IV**. The
  same constants are implemented in `scripts/InspectBackup.java`; change one and the other stops
  reading real backups.

#### How damage to the shared inputs presents

Password, salt and IV feed every payload, so damaging any of them affects all record types at
once. They do **not** fail the same way, and the difference matters when diagnosing a file:

| Damaged input | Symptom |
|---|---|
| Password or salt | wrong derived key, so every payload throws `BadPaddingException` |
| IV | **no exception at all** |

The IV case is the trap. In CBC mode the IV only affects the *first* plaintext block, and the
PKCS5 padding lives in the *last* one — so `decrypt()` validates and returns successfully, having
garbled 16 bytes. Anything that treats "decrypted without throwing" as "valid" will happily print
the result. `scripts/InspectBackup.java` therefore checks that the plaintext is actually a JSON
array before counting it; before that check it reported a bit-flipped IV as `0 record(s)` and
exited `0`, i.e. a corrupt backup looked like an empty one. (Verified 2026-09-21 by flipping the
16 IV bytes of the committed fixture.)

### Version history

| `BACKUP_VERSION` | Change |
|---|---|
| 1 | original; `creationDate` stored as a **1-byte** value |
| 2 | `creationDate` widened to an **8-byte** `Long` |
| 3 | adds `AUTHENTICATOR_DATA_KEY` (TOTP records) |

**Encoding of key `"0"`.** One byte, written with `BACKUP_VERSION.toByte()` and read as
**unsigned** (`RestoreDataWorker.readBackupVersion`). **`0xFF` is reserved**: single-byte versions
are capped at 254, enforced by `BackupDataWorkerTest.backupVersion_shouldFitInOneHeaderByteBelowReservedMarker`.
When a version ≥ 255 is needed, write `[0xFF, <4-byte big-endian int>]`. Every build from #249 on
reads one unsigned byte, sees 255 and rejects the file as too new (`BACKUP_TOO_NEW`). A plain wide int
would not: `[0x00, 0x00, 0x01, 0x00]` reads as version 0 on such a build and would be restored.
(Builds before #249 never check the version, so no encoding protects them.)
The reader then branches on length (1 byte → legacy, 5 bytes with `0xFF` → wide).
`scripts/InspectBackup.java` prints the byte unsigned too; its `--header` output feeds the
seed/fixture version checks, so it must follow the same encoding change.

Both `creationDate` widths are still handled on read:

```kotlin
val creationDate = if (creationDateBytes.size >= Long.SIZE_BYTES) {
    ByteBuffer.wrap(creationDateBytes).long
} else {
    creationDateBytes[0].toLong()
}
```

Older files simply have no entry for newer keys, and the reader uses `importMap[KEY]` without `!!`,
so absence is safe. **Preserve that property when adding a key.**

### Deserialization hardening

`RestoreDataWorker` subclasses `ObjectInputStream` and overrides `resolveClass` with an allowlist:
`java.util.HashMap`, `LinkedHashMap`, `Map`, `String`, `[B`, `Number`, `Integer`, `Long`. Anything
else throws `InvalidClassException`. **Do not widen this** — a password manager deserializing
arbitrary classes from a user-supplied file is a remote-code-execution primitive.

> [!WARNING]
> **A `resolveClass` allowlist and an `ObjectInputFilter` allowlist are not interchangeable.** They
> are consulted on different sets of classes, so copying one into the other fails. Reading a real
> `.bak` through a filter — as `scripts/InspectBackup.java` does — requires exactly:
>
> ```
> java.util.LinkedHashMap, java.util.HashMap, [Ljava.util.Map$Entry;, [B
> ```
>
> `[Ljava.util.Map$Entry;` is reached through the class descriptors and is **invisible to
> `resolveClass`**, which is why the list above omits it. Conversely `java.lang.String` is never
> seen by a filter, because strings are written as `TC_STRING` with no class descriptor. Established
> 2026-09-21 by instrumenting the committed fixture after a copied allowlist rejected every valid
> file.

## Backup outcomes

`BackupDataWorker` reports every non-success as `Result.failure(BackupFailureReason)` (#274):

| Value | Trigger | Clears the folder setting? | Notification |
|---|---|---|---|
| `NOTHING_TO_BACKUP` | vault is empty (`hasAnyRecord` false) | no | none |
| `FOLDER_INACCESSIBLE` | pre-check fails (`exists() && isDirectory && canWrite()`), or `SecurityException` / `FileNotFoundException` while writing | **yes** | "Backup Failed! Please set backup path" |
| `WRITE_FAILED` | any other `IOException` while writing, incl. a null `createFile` / file descriptor | no | check free space |
| `UNKNOWN` | everything else: DAO, crypto, missing input | no | report via Settings > Send feedback |
| `SESSION_LOCKED` | the vault locked before or during the run (`SessionLockedException`), or no password is registered for this work id — the process that registered it is gone | no | **none**: the auto-backup after login stays silent, and the gate has already taken the manual dialog away with HomeGraph (it maps to the generic failure state) |

- **Order:** empty check, then the start notification, then the folder pre-check, then encryption.
  An empty vault never touches the folder, and a dead folder costs no crypto work.
- **Before #274 every failure cleared the folder setting,** so any unrelated error silently stopped
  auto-backup after login. Only a folder that is really gone is cleared now.
- `NOTHING_TO_BACKUP` logs `BACKUP_DATA_NOTHING_TO_BACKUP`, never `BACKUP_DATA_FAILURE`, and posts no
  notification: auto-backup runs on every password login and would nag every empty vault.
- `BACKUP_DATA_SUCCESS` is logged only when a file was written. No folder set is a silent success.
- The manual backup dialog maps each reason to its own `WorkflowState` with an OK button. `UNKNOWN`
  does not reuse `FAILED`, whose password field invites a retry that fails the same way.
- `BackupMetadataRepositoryImpl` releases the persisted SAF grant when the folder is cleared or
  replaced by a different one. At the per-app cap (512 in current AOSP) the system silently prunes
  the oldest grants, so this is hygiene, not a fix for a visible failure.
- The enum is append-only; `BackupFailureReasonTest.entries_shouldKeepPersistedOrdinalOrder` pins it.
- **The backup password never enters WorkManager's database** (envelope MR 4). Whoever enqueues
  puts it in `BackupPasswordRegistry` (`worker/`, process memory, keyed by work id) *before*
  `enqueueUniqueWork`; the worker `take`s it as its first statement using its own `id`, zeroes it
  in a `finally`, and the request's input data carries only the notification flag. A lock clears
  the registry together with the work. The same applies to restore (`RESTORE_PARAM_FILE_URI` is
  the only input).

## Restore semantics

`restoreDataToDb` runs inside `safeBoxDatabase.runInTransaction { }` and, per table, does
`deleteAllData()` followed by bulk insert.

> [!IMPORTANT]
> Restore is a **destructive replace, not a merge**. After a restore the record count equals the
> file's count exactly. Any test asserting additive behaviour is wrong.

> [!WARNING]
> The password is only verified by decrypting a record payload. A backup with **no payloads** has
> nothing to verify, so any password would pass and the replace would wipe the vault. Since #272
> such a file is rejected as `BACKUP_EMPTY` **before** decryption or any DB work. No genuine backup
> is affected: `BackupDataWorker.hasAnyRecord` (formerly `shouldExport`) has skipped writing a file
> for an empty vault since backups were introduced (#111), pinned by
> `BackupAndRestoreWorkersTest.exportToBackupFile_withEmptyVault_shouldFailAsNothingToBackupAndWriteNoFile`.
> A payload that decrypts to `[]` still restores, because decrypting it verified the password.

Failure classification is `RestoreFailureReason`:

| Value | Trigger |
|---|---|
| `INCORRECT_PASSWORD` | `BadPaddingException` during decrypt |
| `CORRUPT_OR_INVALID_FILE` | `IOException`, `IllegalArgumentException` (incl. `SerializationException`), bad structure |
| `UNKNOWN_ERROR` | anything else |
| `BACKUP_TOO_NEW` | header version `> BACKUP_VERSION`; checked right after the version is read, before date parsing, decryption or any DB work |
| `BACKUP_EMPTY` | every `decrypt*Data` result in `startRestore` is null (no record payload, so nothing is decrypted); checked after the version gate, before any DB work |
| `SESSION_LOCKED` | no password registered for this work id (process death), or `SessionLockedException` — typically at the first re-encrypted insert inside `runInTransaction`, which rolls the per-table deletes back; the vault is intact. Logged at `Timber.i`, no `RESTORE_DATA_FAILURE` |

The enum is **append-only**: `toWorkData()` persists the ordinal in WorkManager output data, which
can outlive an app upgrade. `RestoreFailureReasonTest.entries_shouldKeepPersistedOrdinalOrder` pins
the order.

`BACKUP_TOO_NEW` is an all-or-nothing rejection, by decision on issue #249: a newer backup is not
partially restored even when the change was purely additive (a new map key). It only protects
builds that contain the check — builds released before it still accept newer backups, silently
dropping unknown keys or failing as "corrupt file".

### Authenticator records are filtered, not fatal

`filterDecodableAuthenticatorData` drops authenticator rows that cannot produce a code — an
undecodable Base32 seed, an unsupported digit count, a non-positive period — and counts elements
that failed to deserialize at all. The rest of the restore proceeds.

This is deliberate: one bad 2FA seed must not cost the user every login, card and note in the
backup. A skipped count is surfaced to the UI.

## Room

- Current version **6** on `feature/envelope-encryption` (**5** on `master` until the final merge);
  exported schemas live in `app/schemas/` and are also mounted as androidTest assets so migration
  tests can read them.
- `Migration.ALL` in `data/db/Migration.kt` is the canonical list; `CacheModule` does
  `.addMigrations(*Migration.ALL)`. `MigrationTest` asserts
  `Migration.ALL.maxOf { it.endVersion } == currentSchemaVersion()`, so bumping the DB without
  adding a migration fails loudly.
- **No `fallbackToDestructiveMigration` anywhere.** Adding one would convert a migration bug into
  total, silent data loss.
- `MIGRATION_4_5` only **adds** the authenticator table. It does not `ALTER` any encrypted table,
  which is why it is low risk.
- `MIGRATION_5_6` (envelope encryption, [design §4.2](../design/envelope-encryption.md#42-migration_5_6-runs-when-room-opens-before-any-login))
  creates `vault_meta` (`key TEXT PRIMARY KEY, value TEXT`) and `quarantined_record`, parks
  `crypto_version = 1` (only when a `user_details` row exists) and the master-password hash as
  `legacy_password_hash` in `vault_meta`, then rebuilds `user_details` **without** the `password`
  column. The rebuild is create-copy-drop-rename, not `DROP COLUMN`: that needs SQLite 3.35 and
  API 29–33 ship 3.28–3.32. It touches no encrypted record column. Each destructive step is
  preceded by a row-count `check`; a failed check throws, Room rolls the transaction back and the
  database stays at version 5 (the app fails to open rather than opening without its credential).
  Key names and values are SQL literals in the migration and constants in
  `data/db/models/VaultMetaKey.kt`; `MigrationTest` asserts through the constants so the two
  cannot drift apart, and also opens the migrated file through Room to drive the production DAOs.
  Until MR 5 lands, signup still creates a legacy vault (`crypto_version = 1` + hash, written by
  `UserDetailsRepositoryImpl` in one `withTransaction`, `crypto_version` first because
  `UserDetailsDaoSecure` reads it to pick the hint's stored form), so a vault made by an interim
  branch build is migrated like any other.

## Related

- [Testing strategy](../testing/testing-strategy.md)
- [Upgrade testing](../testing/upgrade-testing.md)
- [TOTP record type design](../TotpAuthRecordTypeDesign.md)
