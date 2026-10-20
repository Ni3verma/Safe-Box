# 11. Envelope-encryption spike results (S1–S11)

Date: 2026-10-21

Status: Accepted — evidence record for [docs/design/envelope-encryption.md](../design/envelope-encryption.md) §10

## Context

The envelope-encryption design was written against platform documentation and SDK sources. Its
§10 lists eleven claims that had to be proven or measured on real devices before MR 1 could be
planned: that time-bound and per-operation Keystore keys behave as described through
`androidx.biometric:1.1.0`, that the OAEP wrap interoperates across providers, how long RSA
key generation and PBKDF2 take, how long the one-transaction migration takes, and how SQLite
behaves on a full disk. This record holds the measured values; §2.7 of the design quotes them.

Everything was measured with a throwaway instrumentation project (`scratch/spike/`, never
committed — design §15.2) that drives `BiometricPrompt` and the system PIN screens with UI
Automator, simulates fingerprint touches through the emulator console, and reports each value to
logcat (`SPIKE` tag) and the instrumentation stream. Devices:

| Label | Device | API | Image / build | Lock screen, biometric |
|---|---|---|---|---|
| Pixel 6a | Google Pixel 6a (Tensor; TEE + Titan M2 StrongBox) | 35 | `AP4A.240925.010` userdebug | PIN, fingerprint — **the timing reference device** |
| API 24 | emulator, `google_apis` arm64 r29 | 24 | `sdk_google_phone_arm64:7.0/NYC` | PIN via the Settings wizard; fingerprint via the console |
| API 28 | emulator, `google_apis` arm64 r2 | 28 | `sdk_gphone_arm64:9/PSR1.210301.009.B6` | `locksettings` PIN; fingerprint via the console |
| API 30 | emulator, `google_apis` arm64 r16 | 30 | `sdk_gphone_arm64:11/RSR1.240422.006` | same |
| API 34 | emulator, `aosp_atd` arm64 r2 — the CI image, also under GMD | 34 | `sdk_slim_arm64:14/UE1A.230829.036.A1` | **cannot have a lock screen** (S8) |
| API 35 | emulator, `google_apis_playstore` arm64 | 35 | `sdk_gphone64_arm64:15/AE3A.240806.036` | PIN, fingerprint |

All emulators ran on an Apple-silicon host with the three animation scales at 1.0. Emulator
timings describe the host CPU, not any phone; only the Pixel 6a numbers size budgets.
Percentiles are nearest-rank over the stated `n`, so with 25 runs P99 is the maximum.

## Results

### S1 — a time-bound key through androidx 1.1.0 on API 24–29; sizing `T`

`setUserAuthenticationValidityDurationSeconds(5)`, RSA-2048 OAEP, unwrap in
`onAuthenticationSucceeded`. "Submit" is the instant UI Automator pressed Enter on the system PIN
screen, the earliest moment Gatekeeper could have stamped the authentication.

| Path | API | Runs | Submit/touch → callback (median / P99) | Callback → `doFinal` done (median / max) | Submit → `doFinal` P99 |
|---|---|---|---|---|---|
| PIN, credential Activity, `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` | 24 | 25/25 ok | 326 / 1 121 ms | 12 / 30 ms | **1 138 ms** |
| PIN, credential Activity | 28 | 25/25 ok | 574 / 630 ms | 4 / 38 ms | **635 ms** |
| Fingerprint, androidx dialog (`androidx.core` pinned to 1.17.0, see S1-b) | 24 | 8/8 ok | 14 / 26 ms | 4 / 11 ms | — |
| Fingerprint, framework `FingerprintManager`, no CryptoObject | 24 | 8/8 ok | 4 / 7 ms | 3 / 6 ms | — |
| Fingerprint, framework `BiometricPrompt` via androidx | 28 | 8/8 ok | ≈ 0 / 3 ms | 5 / 13 ms | — |
| Plain keyguard unlock, no prompt (the §1.3 residual) | 24, 28 | — | unwrap succeeded 5–23 ms after the unlock; `UserNotAuthenticatedException` again 6 s later | | |

Both halves of the claim hold: a PIN entered in the credential Activity and a fingerprint each
authorise the time-bound key. The P99 of the credential path is above the 600 ms bar the design
set for a 3 s window on both levels, on a laptop-class host, so **`T` stays 5 s**. The key window
closed 3.5–7 s after each authentication (probe polled every 500 ms), i.e. `T` is honoured.

**S1-b — androidx cannot reach the sensor on API 24–28 with the app's dependencies.**
`androidx.core:core` **1.18.0** replaced `FingerprintManagerCompat` with a stub
(`isHardwareDetected()` compiles to `return false`, `authenticate()` to `return`; verified with
`javap` on 1.17.0 = real, 1.18.0 / 1.19.0 / 1.19.1 = stub). `androidx.biometric:1.1.0` uses that
class for `BiometricManager.canAuthenticate` on API ≤ 28 and for the fingerprint dialog on
API ≤ 27. The app resolves core **1.19.1** (`coreKtx`, bumped to 1.18.0 in #176 on 2026-04-12,
shipped in every release since). Measured with the app's resolution:

| API | `canAuthenticate(BIOMETRIC_STRONG)` | `canAuthenticate(BIOMETRIC_WEAK)` | `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` | Fingerprint prompt |
|---|---|---|---|---|
| 24 (finger enrolled, framework says `hw=true enrolled=true`) | 12 `NO_HARDWARE` | 12 | 0 `SUCCESS` | never shown |
| 28 (finger enrolled) | −1 `STATUS_UNKNOWN` | −1 | 0 | framework prompt works if called |
| 30, 35 | 0 | 0 | 0 | works |

`DefaultDeviceSecurityAuthProvider.canAuthenticate` returns `result == BIOMETRIC_SUCCESS`, so
biometric login is not offered on API 24–28 today; the credential flows (forgot password) are
unaffected. With core pinned to 1.17.0 the same API 24 emulator reports `SUCCESS` and the androidx
fingerprint dialog authorises the key (table above).

### S2 / S9 — health-probe semantics, invalidation, lock-screen removal

Probe = `keyStore.getKey(alias)` then `Cipher.init(DECRYPT_MODE, key, oaepSpec)`; never `doFinal`.

| State | Key | 24 | 28 | 30 | 34 | 35 emulator / Pixel 6a |
|---|---|---|---|---|---|---|
| Healthy, no recent auth | time-bound (5 s) | `UserNotAuthenticatedException` | same | same | n/a (no lock) | same |
| Healthy, no recent auth | per-op device / biometric | init ok; `doFinal` → `IllegalBlockSizeException` (cause `Key user not authenticated`) | same (biometric) | same | n/a | same |
| After lock-screen removal | time-bound | `KeyPermanentlyInvalidatedException` at init | `getKey` → `UnrecoverableKeyException` | `getKey` → `UnrecoverableKeyException` | n/a | not run on the owner's devices |
| After lock-screen removal | per-op biometric | **init ok**, `doFinal` → `Key user not authenticated` | `getKey` → `UnrecoverableKeyException` | `getKey` → `UnrecoverableKeyException` | n/a | — |
| After a new fingerprint is enrolled | per-op biometric | **init ok** (emulator; see note) | not run | `KeyPermanentlyInvalidatedException` at init | n/a | — |
| After a new fingerprint is enrolled | per-op device, time-bound | unaffected | — | unaffected | — | — |

- `containsAlias` stayed `true` for every invalidated key. On API 30, `deleteEntry` on such an
  alias threw `KeyStoreException: Failed to delete entry`, so a replacement under the same alias
  could not be generated until keystore dropped the entry by itself minutes later.
- The API 24 "init ok after new enrolment" is most likely the N emulator's fingerprint HAL
  reporting a constant authenticator id (the framework's init-time check compares the key's
  secure ids with `GateKeeper` and `FingerprintManager.getAuthenticatorId()`); real devices are
  documented to throw at init. The design nevertheless treats a `doFinal` failure after a
  successful prompt as *Invalidated*, because that is what the probe cannot see.
- **Public-key wrap on API 24:** `Cipher.getInstance` for the Keystore public key picked
  `AndroidKeyStoreBCWorkaround` and the ENCRYPT operation on a per-operation key failed with
  `Key user not authenticated`. Re-encoding the public key (`X509EncodedKeySpec`) and encrypting
  with the default provider (BC on 24, Conscrypt on 28+) works for every key on every API.

### S3 — per-operation key, `CryptoObject`, API 30+

`setUserAuthenticationParameters(0, AUTH_DEVICE_CREDENTIAL or AUTH_BIOMETRIC_STRONG)`, prompt
`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` with `CryptoObject(cipher)`, `doFinal` on the returned cipher.

| Path | Device | Runs | Submit/touch → callback (median / max) | Callback → `doFinal` |
|---|---|---|---|---|
| PIN ("Use PIN" then SystemUI credential view) | API 30 | 10/10 | 1 030 / 1 065 ms | 2 / 4 ms |
| PIN | API 35 emulator | 10/10 | 1 052 / 1 098 ms | 2 / 3 ms |
| PIN | **Pixel 6a** | 10/10 | 1 202 / 1 381 ms | 15 / 37 ms |
| Fingerprint | API 30 | 5/5 | 398 / 402 ms | 2 / 3 ms |
| Fingerprint | Pixel 6a | not completed — no finger on the sensor during the run; the PIN path already covers the `CryptoObject` claim | | |

A plain keyguard unlock does **not** authorise the per-operation key (API 30, 35 emulator: both a
cipher initialised before the unlock and a fresh one fail with `IllegalBlockSizeException`), but
it **does** authorise a `setUserAuthenticationParameters(5, …)` time-bound key on API 30 and 35 —
the reason the design uses per-operation keys from API 30.

### S4 — OAEP provider interoperability and StrongBox fallback

Encrypt side × decrypt side, 32-byte DEK, RSA-2048 generated in Keystore:

| Encrypt with | Params | Keystore decrypt, default | Keystore decrypt, explicit MGF1-SHA1 | API |
|---|---|---|---|---|
| Keystore public key (`AndroidKeyStoreBCWorkaround`) | default | ok | ok | 24 |
| Keystore public key (routed to `AndroidOpenSSL`) | default | `IllegalBlockSizeException` | `IllegalBlockSizeException` | 28, 30, 34, 35 |
| JCA key, default provider (BC on 24, Conscrypt on 28+) | default | fails on 24–35 | fails | all |
| JCA key, default provider | explicit MGF1-SHA1 | **ok** | **ok** | all |
| JCA key | explicit MGF1-SHA256 | — | fails | all; decrypt with explicit SHA-256: `InvalidAlgorithmParameterException: Only SHA-1 supported` (24–30), ok (34), `InvalidKeyException` (35) |

The explicit `OAEPParameterSpec(…, MGF1ParameterSpec.SHA1, …)` on the **encrypt** side is the
one combination that works everywhere; a mismatch surfaces as `IllegalBlockSizeException`, not
`BadPaddingException`. `setIsStrongBoxBacked(true)` threw `StrongBoxUnavailableException` on every
emulator (28–35) and the retry without it took 29–267 ms; on the Pixel 6a it produced a
`securityLevel=2` key.

### S5 — RSA-2048 key generation and private operations

| Device | Backing | Keygen (n=5, median / max) | Private unwrap (n=10, median) | `init` only (probe) | Public wrap |
|---|---|---|---|---|---|
| **Pixel 6a** | TEE | **781 / 1 494 ms** | **36 ms** | 8 ms | 3 ms |
| **Pixel 6a** | **StrongBox** | **5 190 / 18 221 ms** (the biometric per-op key took 19 547 ms) | **306 ms** | 37 ms | 4 ms |
| API 28 emulator | software | 130 / 645 ms | 15 ms | 3 ms | 1 ms |
| API 30 / 34 / 35 emulator | software | 50–60 / 96–185 ms | 2–5 ms | 1–3 ms | < 1 ms |

Auth-bound variants generated on every device with a lock screen; on the `aosp_atd` image
`generateKeyPair()` threw `InvalidAlgorithmParameterException` wrapping
`IllegalStateException("Secure lock screen must be enabled…")` / `("At least one biometric must be
enrolled…")`.

### S6 / S10 — PBKDF2 provider and throughput

`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")` is served by **BouncyCastle** on every level
(BC 1.54 on API 24, 1.57 on 28, 1.77 on 35); `PBKDF2WithHmacSHA256` likewise, and it is absent on
API 24. Conscrypt provides the HMACs but no PBKDF2.

| Measurement (ms per 100 000 iterations unless stated) | Pixel 6a, app in foreground | API 28 emulator | API 24 emulator |
|---|---|---|---|
| SHA-1, 160-bit output (one block) | 464 | 829 | 189 |
| SHA-1, 256-bit output (two blocks — the design's choice) | **930** | 1 577 | 358 |
| SHA-256, 256-bit output | 505 | 731 | n/a |
| Conscrypt `Mac.doFinal` × 100 000 (a hand-rolled loop's floor) | 301 | 858 | 212 |
| 600 000 iterations, SHA-1/256-bit | **5.2 s** | 9.1 s | — |
| 1 300 000 iterations, SHA-1/256-bit | **11.1 s** | 20.5 s | — |
| Iterations that fit 125 ms (SHA-1/256-bit) | **≈ 15 000** | ≈ 7 400 | — |

The design's floor (600 000) and target (1 300 000) at ≤ 125 ms on the Pixel 6a are off by a
factor of 40–90; see Decisions.

### S7 — migration duration (five encrypted columns per row, one transaction, verify-after-encrypt)

| Rows | Pixel 6a total (n=3, median) | per legacy field (Keystore AES-GCM decrypt) | per field re-encrypt + verify (in-process) | peak WAL / DB | read all fields, legacy key → DEK |
|---|---|---|---|---|---|
| 100 | 1.39 s | 2.49 ms | 0.26 ms | 0.70 | 1.22 s → 0.04 s |
| 500 | 7.26 s | 2.66 ms | 0.25 ms | 0.92 | 6.31 s → 0.17 s |
| 2 000 | **27.4 s** | 2.47 ms | 0.22 ms | 0.98 | 24.9 s → 0.63 s |

Emulators (host CPU): 2 000 rows in 6.0 s (API 24, 0.53 ms per legacy field) and 12.8 s (API 34,
1.2 ms). The Keystore round-trip dominates everything; the records list is 40× faster once fields
are under the in-process DEK.

### S8 — `locksettings` on the CI image

On the `aosp_atd` API 34 image — booted by hand and under Gradle Managed Devices
(`pixel8Api34`) — `locksettings set-pin 0000` answers *"The device does not support lock screen -
ignoring the command."*, `KeyguardManager.isDeviceSecure` stays `false`, and `pm list features`
lacks `android.software.secure_lock_screen` (present on the Play image). Auth-bound keys cannot be
generated there (S5). `locksettings` also **does not exist on API 24** (`/system/bin/sh:
locksettings: not found`); the PIN was set through the Settings wizard, and running the missing
binary through `UiAutomation.executeShellCommand` killed the instrumentation twice.

### S11 — disk full during the 2 000-row transaction

Filling the data partition from the app itself (5.6 GB on both emulators) and running the
migration: `SQLiteFullException: database or disk is full (code 13 SQLITE_FULL)` inside the
transaction on API 24 and 34; afterwards `crypto_version` = 1, 2 000 rows, `PRAGMA
integrity_check` = ok, every row still decrypts under the legacy key, WAL file 0 bytes. After
freeing the space the retry committed with a peak WAL of **0.98 × DB size** and every row
decrypting under the DEK.

## Decisions

1. **`T` = 5 s on API 24–29**; the 3 s option is closed (S1).
2. **Migration progress is determinate** (`progress / total`): 100 records already take 1.4 s on
   a flagship, 2 000 take 27 s (S7). The design's "indeterminate unless P95 > 3 s" branch is
   removed. `LegacyVaultMigrator` may decrypt legacy fields on a small worker pool ahead of the
   single write transaction — the cost is a serial Keystore round-trip per field.
3. **Wrap with the JCA provider on the re-encoded public key, explicit MGF1-SHA1, never with the
   Keystore cipher** (S2, S4). A mismatch is an `IllegalBlockSizeException`.
4. **`KeyHealth` maps alias-missing, `getKey == null`, `UnrecoverableKeyException`,
   `KeyPermanentlyInvalidatedException` *and* a `doFinal` failure after a successful prompt to
   *recreate*; a failed `deleteEntry` is non-fatal and the replacement gets a fresh alias** (S2/S9).
5. **`PBKDF2_ITERATIONS` is an open decision** for the owner (design §2.4 lists the options with
   the measured numbers). The design no longer states 600 000 / 1 300 000.
6. **StrongBox is kept for `KEK_Device`** (recovery path, rare: 306 ms unwrap, keygen in the
   background) **and reconsidered for `KEK_Biometric`**: a 5–20 s background keygen is acceptable
   only because it is lazy and silent, but 306 ms on every biometric login is user-visible; the
   TEE key (36 ms) is the default unless the owner prefers the StrongBox property.
7. **`KeystoreWrapKeysTest` cannot run on the CI image** (S8). It needs a GMD definition on an
   image with `android.software.secure_lock_screen` (to be verified before MR 9) or stays in the
   manual matrix.
8. **The `androidx.core` ≥ 1.18 regression is tracked as [#299](https://github.com/Ni3verma/Safe-Box/issues/299) and must be fixed before MR 6**
   (biometric login): upgrade `androidx.biometric` to a release that does not depend on
   `FingerprintManagerCompat`, or drop biometric support below API 28 explicitly. It also affects
   today's app.

## Consequences

- Design §2.2, §2.3, §2.4, §2.7, §3.7, §6.4, §6.6, §9.3, §10, §12 and decision 10 were rewritten
  to the measured values in the same change (MR 0).
- Manual test plan: the API 24 row cannot use `locksettings`; the lock screen is set through
  Settings. The API 34 `aosp-atd` row cannot run any auth-bound scenario.
- The spike project is not kept. Re-running it means recreating `scratch/spike/` from this
  record's method section; the ten result files it produced are summarised above.
