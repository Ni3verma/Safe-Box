# Persistence and cryptography

How Safe-Box stores data, what encrypts what, and where the sharp edges are.

## Storage layers

There are **six** independent persistence layers. Most bugs that destroy user data involve one of
them getting out of step with the others, which is why a Room migration test alone is never
sufficient coverage.

| Layer | Holds |
|---|---|
| Room (`SafeBoxDatabase`) | all vault records, per-field encrypted; `user_details` (password hash, and the hint encrypted with the same key as the records — `UserDetailsDaoSecure.getHint`) |
| `EncryptedSharedPreferences` | signup state, other secrets |
| Plain `SharedPreferences` | login counters, permission-asked flags |
| DataStore | settings/preferences |
| `AndroidKeyStore` | the field-encryption key — **not** file-backed, not in `/data/data` |
| WorkManager's own DB | scheduled backup and clipboard-clear work |

## Field encryption

`di/SecurityModule.kt`:

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
> **The highest-severity failure mode in the app.** If the alias is ever lost, this silently
> creates a *new* key. Nothing throws *at key creation*. Every existing record becomes permanently
> undecryptable. Observed with the alias deliberately deleted (2026-09-24, upgrade harness): the
> app launched normally and then crashed with `javax.crypto.AEADBadTagException` from
> `AndroidKeyStoreCipherSpiBase.engineDoFinal` at the first decrypt, which was the unlock screen's
> Show Hint, since the hint is encrypted under the same key.
>
> Because the key lives in the Keystore and not in `/data/data`, it is invisible to Room migration
> tests, to backup/restore tests, and to any `/data` snapshot. The only thing that can catch a
> regression here is an **upgrade test that installs an old build, writes records, upgrades in
> place, and compares decrypted field values**. See
> [docs/testing/upgrade-testing.md](../testing/upgrade-testing.md).

Encryption is per-field, applied in the `secureDao` layer
(`data/db/secureDao/*DaoSecure.kt`), so DAOs above it deal in plaintext.

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

## Restore semantics

`restoreDataToDb` runs inside `safeBoxDatabase.runInTransaction { }` and, per table, does
`deleteAllData()` followed by bulk insert.

> [!IMPORTANT]
> Restore is a **destructive replace, not a merge**. After a restore the record count equals the
> file's count exactly. Any test asserting additive behaviour is wrong.

Failure classification is `RestoreFailureReason`:

| Value | Trigger |
|---|---|
| `INCORRECT_PASSWORD` | `BadPaddingException` during decrypt |
| `CORRUPT_OR_INVALID_FILE` | `IOException`, `IllegalArgumentException` (incl. `SerializationException`), bad structure |
| `UNKNOWN_ERROR` | anything else |

### Authenticator records are filtered, not fatal

`filterDecodableAuthenticatorData` drops authenticator rows that cannot produce a code — an
undecodable Base32 seed, an unsupported digit count, a non-positive period — and counts elements
that failed to deserialize at all. The rest of the restore proceeds.

This is deliberate: one bad 2FA seed must not cost the user every login, card and note in the
backup. A skipped count is surfaced to the UI.

## Room

- Current version **5**; exported schemas live in `app/schemas/` and are also mounted as androidTest
  assets so migration tests can read them.
- `Migration.ALL` in `data/db/Migration.kt` is the canonical list; `CacheModule` does
  `.addMigrations(*Migration.ALL)`. `MigrationTest` asserts
  `Migration.ALL.maxOf { it.endVersion } == currentSchemaVersion()`, so bumping the DB without
  adding a migration fails loudly.
- **No `fallbackToDestructiveMigration` anywhere.** Adding one would convert a migration bug into
  total, silent data loss.
- `MIGRATION_4_5` only **adds** the authenticator table. It does not `ALTER` any encrypted table,
  which is why it is low risk.

## Related

- [Testing strategy](../testing/testing-strategy.md)
- [Upgrade testing](../testing/upgrade-testing.md)
- [TOTP record type design](../TotpAuthRecordTypeDesign.md)
