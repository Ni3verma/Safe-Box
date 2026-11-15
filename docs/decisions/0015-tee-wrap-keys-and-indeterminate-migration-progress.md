# 15. TEE wrap keys and an indeterminate migration progress state

Date: 2026-11-15

Status: Accepted. Supersedes ADR-0011 decision 6 (StrongBox for `KEK_Device`) and the S7
"determinate progress" decision; amends [design §2 and §6.4](../design/envelope-encryption.md).

## Context

The final manual matrix ran the migration on the reference Pixel 6a (Android 15, Titan M2
StrongBox) over a real v2.2.5.0 QA vault of 11–12 records:

| Run | `duration_ms` | `KEK_Device` keygen | `KEK_Biometric` keygen | legacy decrypt (all records) |
|---|---|---|---|---|
| 2026-11-15 01:18 | 17 486 | 8.5 s | 8.7 s | 109 ms |
| 2026-11-15 01:54 | 20 667 | 17.1 s | 3.3 s | 102 ms |

Both wrap keys were generated StrongBox-first, inline in the migration before the record pass, so
the progress card sat at "0 of N records" for the whole run; S5 puts the StrongBox maximum at
18–20 s per key, so 40 s was possible. ADR-0011 had kept StrongBox for `KEK_Device` only because its
keygen would be "in the background", which the migration does not do.

Production telemetry from `v2.3.5.0-rc2` (BigQuery export, read 2026-11-15, window 2026-10-20 →
2026-11-15):

| Query | Result |
|---|---|
| `vault_stats`, largest `record_count` per user (148 users) | P50 0, P90 30, **P95 48, max 176**; nobody over 200 |
| `login_success`, last `strongbox` per user (77 users) | **28 with StrongBox (36 %)**, 49 without |

At the measured ~9 ms per legacy record, the largest production vault decrypts in about 1.6 s.

## Decision

1. **Both wrap keys are generated in the TEE**; `setIsStrongBoxBacked` and the StrongBox-first
   retry are removed from `AndroidKeystoreGateway`. TEE keygen is 0.8 s median, 1.5 s max (S5), and
   the private unwrap on every fingerprint unlock drops from 306 ms to 36 ms. Keys stay
   hardware-backed, non-exportable and auth-bound; StrongBox keys already created on a device stay in
   use until they are invalidated and regenerated.
2. **The migration progress is one indeterminate state.** `MigrationPhase`, the done/total counter
   and the 90/10 bar split are deleted; `MigrationProgressState` is `Idle` / `Running`. The card shows
   *Upgrading vault security…* / *This takes a few seconds.* over an indeterminate bar. The design's
   own threshold ("P95 `record_count` under 200 means indeterminate progress only") is met.
3. **No timeout or *Continue for now* on a slow migration.** With TEE keys the whole run is a few
   seconds (×4 derating: about 6–12 s worst case), and the wraps are already optional — a failed
   keygen commits without that wrap and post-unlock maintenance recreates it. The existing failure
   card covers real errors.

## Readiness read-back (same window)

The other readiness questions of [envelope-readiness.md](../analytics/envelope-readiness.md), read
with recipes 3 and 5 and an active-users split by OS:

| Question | Result | Outcome |
|---|---|---|
| Does the legacy alias disappear in the field? | `keystore_key_created`: 272 events, 269 users, **all `db_exists = 0`** | the silent-data-loss path never fired; the `db_exists` dimension was archived in GA (debt row D12 closed) |
| Forgot-password use by Android version | 56 `forgot_password_click` from 12 users, 3 completed resets (Android 14, 9, 8.1); **Android 10: 0 taps** | recovery stays |
| Active users by OS (`login_success`) | Android 16: 30, 17: 17, 15: 8, 14: 7, 13: 6, 11: 3, 9: 3, 12: 2, 8.1: 1, 7.0: 1; **Android 10: 0** | the owner kept the API 29 time-bound recovery key (design residual 1): with no Android 10 users the exposure is near zero |

## Consequences

- Manual matrix rows B7 (2 000-record progress mode) and D4 (StrongBox-backed `KEK_*`) are dropped.
- The `strongbox` param on `login_success` stays: it reports the device capability, now unused by
  key generation.
- If vaults ever grow past a few hundred records (re-read `vault_stats` P95), the determinate
  counter is the thing to bring back; this ADR is where the threshold came from.
