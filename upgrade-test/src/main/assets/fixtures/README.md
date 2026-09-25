# Upgrade-test fixtures

Golden backup files used by the upgrade test suite to seed a baseline vault before an
APK-over-APK upgrade. See [docs/testing/upgrade-testing.md](../../../../../docs/testing/upgrade-testing.md)
for the design these serve.

> [!CAUTION]
> These files are **unreproducible**. Each one captures a backup format produced by an app version
> that no longer exists in the codebase. Do not delete, regenerate, or "clean up" a fixture just
> because its contents look arbitrary — the arbitrariness is the point, and the bytes cannot be
> recreated once the producing version is gone.

## Verifying a fixture

No emulator required. `scripts/InspectBackup.java` decrypts a `.bak` using the same parameters as
`PasswordBasedEncryptionImpl` and prints the record counts and payload:

```bash
JAVA=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home/bin/java
"$JAVA" scripts/InspectBackup.java upgrade-test/src/main/assets/fixtures/v2_pre_totp.bak 'Fixture@Backup1'
```

## `v2_pre_totp.bak`

| | |
|---|---|
| Captured from | `v2.1.4.0-rc3` (Room schema 4, the last version before TOTP) |
| Captured on | 2026-09-21 |
| `BACKUP_VERSION` | **2** |
| Backup file password | `Fixture@Backup1` |
| SHA-256 | `f2b3cc20f6f2b8f9ce25e1690dbcdc3c9400760aedd01cb3326949e7852534f4` |
| Size | 1949 bytes |

### Contents

| Record type | Count | Notes |
|---|---|---|
| `LOGIN` | 2 | one mandatory-only (`url`, `password`, `notes` all null), one fully populated |
| `BANK_ACCOUNT` | 2 | record 1 mixes `""` and `null` across optional fields |
| `BANK_CARD` | 2 | one mandatory-only, one fully populated |
| `SECURE_NOTE` | 1 | `title` and `notes` are both mandatory, so there is no second shape to capture |
| `AUTHENTICATOR` | — | key `"8"` is **absent**; this predates TOTP, which is the whole reason the fixture exists |

`SECURE_NOTE` having one record is correct, not an oversight. Any assertion written against this
fixture must expect 1.

### Why the payload looks like junk

The `notes` fields deliberately contain embedded newlines and characters such as `^&*^` and
`4(**^*((*%$@#`. They exercise JSON escaping across the serialize → encrypt → decrypt →
deserialize round trip. Do not tidy them.

### What is *not* in the file

The vault master password. A `.bak` carries record data only, so restoring this fixture into a
vault leaves that vault's own master password in force. The credentials used when establishing the
baseline vault are listed in the upgrade-testing design, not here.

## `v1_legacy.bak`

The protection for users who still hold a v1 export: a `.bak` outlives the install that wrote it.
Phase D restores it into the build under test and compares every field with
`LegacyFixture.kt`, which holds the displayed form of the values below.

| | |
|---|---|
| Captured from | `v1.4.4.0` QA APK (`versionCode` 6), the first release with backup; by hand, on emulator-5554 |
| Captured on | 2026-09-25 |
| `BACKUP_VERSION` | **1** |
| `creationDate` | the v1 **single-byte** form (value `61`), not an 8-byte timestamp |
| Backup file password | `Upgrade@@Test123`, the same as the v1 vault's master password. v1's stricter password rules refused `Upgrade@Test12`, and the capture reused the replacement for the backup |
| SHA-256 | `da24e2120363cdf29b6b383591cfbee862b3d06fce99e6906e1e96e7a95572ff` |
| Size | 1958 bytes |

### Contents

Decoded with `scripts/InspectBackup.java`. `null` means the field was left empty.

| Type | Title | Stored values |
|---|---|---|
| `LOGIN` | `v1 login min` | user id `v1-user-min`; url, password, notes `null` |
| `LOGIN` | `v1 login full` | url `https://v1.example.com`, user id `v1-user`, password `V1Login@1`, notes `line one\nline two ^&*(` |
| `BANK_ACCOUNT` | `v1 bank min` | account `111122223333`; everything else `null` |
| `BANK_ACCOUNT` | `v1 bank full` | account `444455556666`, customer `V1 Customer` / `C-1001`, branch `BR01` / `V1 Branch` / `1 Legacy Road`, IFSC `IFSC0001234`, MICR `400002001`, notes `bank\nnotes %$#` |
| `BANK_CARD` | `v1 card min` | number `4111111111111111`; everything else `null` |
| `BANK_CARD` | `v1 card full` | name `V1 HOLDER`, number `5500005555555559`, pin `4321`, cvv `123`, expiry **`12/30`**, notes `card\nnotes @!` |
| `SECURE_NOTE` | `v1 note` | notes `legacy note\nsecond line &^%` |
| `AUTHENTICATOR` | — | key `"8"` **absent**; the type did not exist |

Two stored values differ from what was typed, and both are v1 behaviour worth keeping:

- **`V1 HOLDER`**: v1's form capitalised the name while it was typed.
- **`12/30`**: v1 stored the expiry *with* its slash. v2 onwards stores `1230` and adds the slash
  on screen, and strips a stored slash when reading (`BankCardDataDaoSecure.decrypt`). This row is
  the only thing that exercises that legacy path.

The v1 vault itself used master password `Upgrade@@Test123` and hint `fixture 1.4.4.0`. Neither
is in the file.
