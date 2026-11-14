# 14. Envelope encryption: one DEK, three wraps, a session key in RAM

Date: 2026-11-14

Status: Accepted. Detail, flows and evidence are in
[docs/design/envelope-encryption.md](../design/envelope-encryption.md) (§14 lists every decision with
its rationale); the measurements behind them are in [ADR-0011](0011-envelope-encryption-spike-results.md).
Amends ADR-0011 decision 7 (`KeystoreWrapKeysTest` placement).

## Context

Until the envelope release every record field was encrypted under one ambient AndroidKeyStore AES
key (`symmetricDataKey`, no user authentication). Login was a boolean gate, so any code running in
the process — a patched APK, a hijacked navigation route — read plaintext; a lost alias was silently
regenerated and made every record unreadable; and one unreadable row broke the list it was in. The
effort ran as MR T (telemetry on `master`), MR 0 (design and spike) and MR 1–9 on
`feature/envelope-encryption`.

## Decision

1. **Key hierarchy.** One random 32-byte DEK encrypts every field and is never rotated; it exists on
   disk only as wraps in Room `vault_meta`: `wrapped_dek_password` (PBKDF2-SHA256 + AES-GCM),
   `wrapped_dek_biometric` and `wrapped_dek_device` (RSA-2048-OAEP under two Keystore key pairs), plus
   a `dek_verifier` checked on every unwrap. In RAM only in `SessionKeyHolder`; every data-layer call
   while locked fails closed with `SessionLockedException`, and `HomeGraph` routes to Login whenever
   the holder is locked.
2. **Asymmetric Keystore KEKs, two of them.** The public half wraps silently after any unlock; only
   the private half is auth-gated. The biometric key is per-operation and invalidated by enrolment;
   the device key accepts the credential or a strong biometric (per-operation on API 30+, time-bound
   `T` = 5 s on API 29). Keystore wraps are disposable and recreated silently; the password wrap never
   depends on Keystore.
3. **KDF.** Platform `PBKDF2WithHmacSHA256`, 99 000 iterations (≤ 500 ms on the Pixel 6a), NFKC once
   in `PasswordKdf`, versioned `kdf_params`. No native KDF; below OWASP's 2023 minimum, accepted.
4. **Recovery** through `wrapped_dek_device` (whoever knows the PIN can reset the master password —
   the pre-envelope floor). No device wrap ⇒ recovery-not-set-up ⇒ *Erase everything and start over*.
   Settings' change-password gate is device authentication first, current master password second.
5. **Migration.** Password-first, one Room transaction, `crypto_version` in Room as the single source
   of truth, verify-after-encrypt, per-table row counts and a password-wrap round trip before the
   commit; any failure rolls back, and *Continue for now* (login trigger only) opens a legacy session
   so a failure never locks the user out.
6. **MR 5 reversals (owner review, 2026-10-24).** The row quarantine (`quarantined_record`, its
   notice and analytics) and the free-space precheck with its M1–M5 user codes were dropped before
   they shipped. A row the legacy key cannot read (GCM tag or format failure) is deleted inside the
   migration transaction and counted by the row-count check; the legacy alias is deleted after every
   commit; a full disk fails the commit (`db_io`) and rolls back (ADR-0011 S11). Failure copy is keyed
   off `MigrationFailureReason`.
7. **Backup format 4** (#242): a self-describing AES-GCM container with the vault's KDF, its own
   codec, the format 1–3 reader isolated in `LegacyBackupReader`.
8. **Start routing reads Room alone** (`VaultRepository.hasVault()`); `EncryptedSharedPreferences`
   and `IS_SIGN_UP_REQUIRED` are gone (MR 9). The orphaned preference file on upgraded devices — one
   non-secret boolean, never read — is deliberately not cleaned up.
9. **`KeystoreWrapKeysTest` is split, not given its own device (MR 9).** Its CI half (keygen refused
   without a lock screen, `Missing` probe, OAEP interop both ways) runs on the `aosp-atd` GMD image;
   its PIN half is `assumeTrue(isDeviceSecure)` and runs on the API 30 `google_apis` AVD after
   `adb shell locksettings set-pin`. No `google_apis` GMD definition was added; the CI image stays
   `aosp-atd`, which cannot hold a secure lock screen (ADR-0011 S8).
10. **Upgrade harness baseline.** J4 runs from N-1 as resolved by `scripts/lib/previous-release.sh`
    (newest stable release older than the build; release candidates never count). A separate run from
    `v2.1.4.0` was dropped: it carries the same legacy crypto as `v2.2.5.0`, and the DB 4→5→6 chain is
    covered by `MigrationTest`'s 1→6 chain.

## Deferred removals and their triggers

| Register row | What goes | Trigger |
|---|---|---|
| D2 / D3 | `legacy_password_hash` handling and everything marked `// TODO(D3)`: the legacy session, `legacyVaultKey()`, `compareHash`, `LegacyVaultMigrator`, the legacy hint branch | Firebase shows no `vault_migration_*` and no `vault_legacy_session_start` for N months |
| D10 | `LegacyBackupReader` and `PasswordBasedEncryptionImpl` (format 1–3 restore) | `restore_data_success` with `version ≤ 3` is zero for N months |
| D11 | `LegacyVaultMigrator.debugFailStage` test hook | with D3 |
| — | API 29 time-bound `KEK_Device` path | Android 10 share of `forgot_password_click` ≈ 0 after a release ⇒ recovery becomes API 30+ |

## Consequences

- A bypass of the login screen obtains ciphertext only; the residual is the API 29 window of `T`
  after any phone unlock (design §1.3, §1.4).
- A user without a lock screen when they forget the password has no recovery; the Home nudge is the
  mitigation. Legacy `.bak` files already in users' folders stay at the old work factor.
- A record that was unreadable before the upgrade is deleted by it, without a notice;
  `vault_migration_success.records_deleted` (since MR 9) counts those rows.
- The lock-screen half of the Keystore tests is a manual run, recorded with the final PR's manual
  test plan rather than enforced by CI.
