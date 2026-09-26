# Upgrade-test fixtures

Backups in every **older** backup format the app still has to read. The restore test restores each
one into the build under test, checks the screens, backs up, and compares the result with the
fixture field by field. The current format lives in [`../seed/seed.bak`](../seed/README.md). The
design is in [upgrade-testing.md](../../docs/testing/upgrade-testing.md).

> [!CAUTION]
> These files are **unreproducible**. Each one captures a format written by an app version that no
> longer exists in the codebase. Never delete, regenerate or "clean up" a fixture because its
> contents look arbitrary: the arbitrariness is the point, and the bytes cannot be recreated once
> the producing version is gone. `scripts/tests/backup-format-test.sh` fails if one goes missing.

New fixtures are only ever added by `scripts/update-seed.sh`, which archives the outgoing seed as
`format-<k>.bak` when `BACKUP_VERSION` moves to k+1.

## Verifying a fixture

No emulator required. `scripts/InspectBackup.java` decrypts a `.bak` with the same parameters as
`PasswordBasedEncryptionImpl`:

```bash
JAVA=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home/bin/java
"$JAVA" scripts/InspectBackup.java upgrade-test/fixtures/format-2.bak 'Fixture@Backup1'
```

## Provenance

| File | Captured from | Captured on | Backup password |
|---|---|---|---|
| `format-1.bak` | `v1.4.4.0` QA APK (`versionCode` 6), the first release with backup; by hand on an emulator | 2026-09-25 | `Upgrade@@Test123` |
| `format-2.bak` | `v2.1.4.0-rc3` (Room schema 4, the last version before TOTP) | 2026-09-21 | `Fixture@Backup1` (the only exception to the fixed password; see `password_for_format` in `scripts/lib/backup-files.sh`) |
<!-- provenance rows end -->

## Things that look wrong but are not

- **`format-1.bak` stores the card expiry as `12/30`.** v1 kept the slash; v2 onwards stores `1230`
  and strips a stored slash when reading (`BankCardDataDaoSecure.decrypt`). This file is the only
  thing that exercises that path.
- **`format-1.bak` stores the holder name as `V1 HOLDER`.** v1's form capitalised it while typing.
- **`format-1.bak` stores `creationDate` in the v1 single-byte form**, not an 8-byte timestamp.
- **`format-2.bak` has one `SECURE_NOTE`.** Title and notes are both mandatory, so there was no
  second shape to capture. Assertions against it must expect 1.
- **`format-2.bak` mixes `""` and `null`** across the optional fields of one bank account, on
  purpose.
- **The `notes` fields look like junk** (embedded newlines, `^&*^`, `4(**^*((*%$@#`). They exercise
  JSON escaping across the serialise, encrypt, decrypt and deserialise round trip. Do not tidy them.
- **Neither file has authenticators.** The type did not exist before format 3.
- **No vault master password is in any file.** A `.bak` carries record data only.

## Contents

<!-- generated contents start: scripts/update-seed.sh rewrites everything below -->

### `format-1.bak`

- Format version: 1
- SHA-256: `da24e2120363cdf29b6b383591cfbee862b3d06fce99e6906e1e96e7a95572ff`
- Records: 2 BANK_ACCOUNT, 2 BANK_CARD, 2 LOGIN, 1 SECURE_NOTE

| Type | Title | Filled fields |
|---|---|---|
| BANK_ACCOUNT | "v1 bank full" | accountNumber, branchAddress, branchCode, branchName, customerId, customerName, ifscCode, micrCode, notes |
| BANK_ACCOUNT | "v1 bank min" | accountNumber |
| BANK_CARD | "v1 card full" | cvv, expiryDate, name, notes, number, pin |
| BANK_CARD | "v1 card min" | number |
| LOGIN | "v1 login full" | notes, password, url, userId |
| LOGIN | "v1 login min" | userId |
| SECURE_NOTE | "v1 note" | notes |

### `format-2.bak`

- Format version: 2
- SHA-256: `f2b3cc20f6f2b8f9ce25e1690dbcdc3c9400760aedd01cb3326949e7852534f4`
- Records: 2 BANK_ACCOUNT, 2 BANK_CARD, 2 LOGIN, 1 SECURE_NOTE

| Type | Title | Filled fields |
|---|---|---|
| BANK_ACCOUNT | "BA 1" | accountNumber |
| BANK_ACCOUNT | "ba 2" | accountNumber, branchAddress, branchCode, branchName, customerId, customerName, ifscCode, micrCode, notes |
| BANK_CARD | "card 1" | number |
| BANK_CARD | "card 2" | cvv, expiryDate, name, notes, number, pin |
| LOGIN | "login 1" | userId |
| LOGIN | "login 2" | notes, password, url, userId |
| SECURE_NOTE | "note" | notes |
