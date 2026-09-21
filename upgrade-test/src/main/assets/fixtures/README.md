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
