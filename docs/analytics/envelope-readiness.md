# Envelope-encryption readiness telemetry

Events shipped ahead of the envelope-encryption work so that its corner cases are sized with
production data instead of guesses. **Logging only**: every addition is an event, a parameter, or a
`catch` that logs and rethrows. Nothing here changes what the app does.

Each row below names the decision it feeds. Read the numbers between **week 4 and week 8** after
the carrying release reaches production: the BigQuery export is in the sandbox, so its daily tables
expire 60 days after creation ([firebase-setup.md](firebase-setup.md#console-settings)). Put the
results into an ADR under `docs/decisions/` so the design is planned against evidence.

## The events

| Event | Fired by | Params | Bound |
|---|---|---|---|
| `login_success` (existing) | `LoginViewModel.onAuthSuccess` | adds `device_secure`, `biometric_class`, `strongbox`, read through `DeviceSecurityStatus` | per unlock; a failed read degrades the value, never the login |
| `record_decrypt_failure` | `DecryptFailureReportingSymmetricKeyUtils.decrypt`, the decorator `SecurityModule` wraps around `SymmetricKeyUtilsImpl` | `exception` (simple class name) | once per exception class per process; the exception is rethrown unchanged. A Crashlytics non-fatal with the stack is recorded at the same time |
| `keystore_key_created` | `SecurityModule.provideSymmetricKey`, exactly where the alias is found absent | `db_exists` | once per key generation. `db_exists = 1` is the silent-data-loss signature: a vault file exists but the key that encrypted it is gone |
| `vault_stats` | `RecordsViewModel`, on the first record list of the ViewModel | `record_count`, `db_size_kb`, `free_space_mb` (both `-1` when the storage read fails) | once per unlock session |
| `forgot_password_click` | `LoginViewModel` | — | per tap, before any device-security check |
| `device_security_required_dialog_show` and its `*_open_settings_click`, `*_open_settings_fail`, `*_cancel_click`; `update_password_dialog_show`, `*_allow_click`, `*_cancel_click` (all existing) | `LoginViewModel`, `SettingsViewModel` | adds `source` ∈ {`login`, `settings`} | unchanged; the param tells the forgot-password flow from a routine password change |
| `backup_data_success` (existing) | `BackupDataWorker` | adds `version`, `duration_ms`, `size_kb` (`-1` when the document provider cannot report a length) | per completed backup |
| `restore_data_success` (existing) | `RestoreDataWorker` | adds `version`, `duration_ms` | per completed restore |
| `session_timeout` | `ActiveSessionManager`, after the logout event is emitted | `worker_running` | per away-timeout; a WorkManager read failure logs `0` |

Wire values: `device_secure`, `strongbox`, `db_exists`, `worker_running` arrive as `0`/`1`;
`biometric_class` is `BiometricClass.analyticsValue` (`strong`, `weak_only`, `none_enrolled`,
`no_hardware`, `unavailable`); `version` is a `Double`; the five sizes and durations are integers.

What is never logged: plaintext, ciphertext, record titles, table names, exception messages, file
names or paths. `record_decrypt_failure` carries the exception class only; the table is visible in
the non-fatal's stack trace, which names the DAO.

## The questions

| Question | Read | Decision |
|---|---|---|
| How many active users have no lock screen, and which biometric class do they have? | `login_success` by `device_secure`, then by `biometric_class`, per `user_pseudo_id` | the size of the population that password recovery cannot serve, and whether the lock-screen nudge deserves its own UI or a single line |
| Are there rows today's key cannot decrypt? | `record_decrypt_failure` count and distinct users | zero over six weeks lets the quarantine table, its notice and the decision behind them be dropped before they are built |
| Does the Keystore alias disappear in the field? | `keystore_key_created` with `db_exists = 1` | the real frequency of the silent-data-loss path; a number to quote, nothing to delete |
| How big are vaults, how full are phones? | P50 / P95 of `record_count`, `db_size_kb`, `free_space_mb` from `vault_stats` | P95 `record_count` under 200 means indeterminate progress only; nobody under 100 MB free means the free-space precheck stays but its copy goes |
| Does StrongBox matter? | `login_success` share with `strongbox = 1` | whether StrongBox timing needs its own spike |
| How often is forgot-password used, and on which Android versions? | `forgot_password_click` and `update_password_dialog_allow_click` with `source = login`, split by GA's *OS version* dimension (`device.operating_system_version` in BigQuery) | recovery usage on API 24–29 near zero drops the time-bound credential path and everything that exists only for it |
| Which backup formats circulate; how long do backup and restore take? | `version` and `duration_ms` on both success events, `size_kb` on `backup_data_success` only | sizes the PBKDF2 budget of the next format and sets the horizon for dropping the legacy reader |
| Does the away timeout ever hit a running backup? | `session_timeout` with `worker_running = 1` | never means cancel-on-lock stays but its dialog state can be a toast |

## Query recipes

All run against `` `andryoga-safe-box.analytics_286937401.events_*` ``; bound `_TABLE_SUFFIX` to
the read-back window as in [firebase-setup.md](firebase-setup.md#query-recipes). `p(key)` below
stands for the parameter sub-select, written out once:

```sql
-- p(key): one parameter as a string, whatever its wire type.
(SELECT COALESCE(value.string_value, CAST(value.int_value AS STRING), CAST(value.double_value AS STRING))
 FROM UNNEST(event_params) WHERE key = 'KEY')
```

```sql
-- 1. Lock screen and biometric class, counted per user (last login wins).
WITH last_login AS (
  SELECT user_pseudo_id,
         ARRAY_AGG(STRUCT(
           (SELECT value.int_value    FROM UNNEST(event_params) WHERE key = 'device_secure')   AS device_secure,
           (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'biometric_class') AS biometric_class,
           (SELECT value.int_value    FROM UNNEST(event_params) WHERE key = 'strongbox')       AS strongbox
         ) ORDER BY event_timestamp DESC LIMIT 1)[OFFSET(0)] AS l
  FROM `andryoga-safe-box.analytics_286937401.events_*`
  WHERE event_name = 'login_success'
  GROUP BY user_pseudo_id
)
SELECT l.device_secure, l.biometric_class, l.strongbox, COUNT(*) AS users
FROM last_login
GROUP BY 1, 2, 3
ORDER BY users DESC;
```

```sql
-- 2. Undecryptable rows: how often, how many users, which exception.
SELECT (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'exception') AS exception,
       COUNT(*) AS events,
       COUNT(DISTINCT user_pseudo_id) AS users
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name = 'record_decrypt_failure'
GROUP BY exception
ORDER BY events DESC;
```

```sql
-- 3. Keystore key generated: first launches versus a vault that already existed.
SELECT (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'db_exists') AS db_exists,
       COUNT(*) AS events,
       COUNT(DISTINCT user_pseudo_id) AS users
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name = 'keystore_key_created'
GROUP BY db_exists;
```

```sql
-- 4. Vault size and storage headroom, one sample per user (largest vault, least headroom).
--    The -1 sentinel of a failed read is dropped inside each sub-select, so one failed read does
--    not throw away a user's other samples through MIN().
WITH per_user AS (
  SELECT user_pseudo_id,
         MAX((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'record_count'))                          AS record_count,
         MAX((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'db_size_kb'    AND value.int_value >= 0)) AS db_size_kb,
         MIN((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'free_space_mb' AND value.int_value >= 0)) AS free_space_mb
  FROM `andryoga-safe-box.analytics_286937401.events_*`
  WHERE event_name = 'vault_stats'
  GROUP BY user_pseudo_id
)
SELECT COUNT(*) AS users,
       APPROX_QUANTILES(record_count, 100)[OFFSET(50)]  AS p50_records,
       APPROX_QUANTILES(record_count, 100)[OFFSET(95)]  AS p95_records,
       MAX(record_count)                                AS max_records,
       APPROX_QUANTILES(db_size_kb, 100)[OFFSET(95)]    AS p95_db_kb,
       COUNTIF(free_space_mb < 100)                     AS users_under_100mb_free
FROM per_user;
```

```sql
-- 5. Forgot-password use by Android version; the allow click is the one that actually resets.
SELECT device.operating_system_version AS android,
       event_name,
       COUNT(*) AS events,
       COUNT(DISTINCT user_pseudo_id) AS users
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name = 'forgot_password_click'
   OR (event_name = 'update_password_dialog_allow_click'
       AND (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'source') = 'login')
GROUP BY android, event_name
ORDER BY android, event_name;
```

```sql
-- 6. Backup formats in circulation and how long the workers take. size_kb exists only on
--    backup_data_success (NULL for restore rows, which APPROX_QUANTILES ignores); its -1 sentinel
--    is dropped inside the sub-select.
SELECT event_name,
       (SELECT value.double_value FROM UNNEST(event_params) WHERE key = 'version') AS version,
       COUNT(*) AS events,
       APPROX_QUANTILES((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'duration_ms'), 100)[OFFSET(95)]                        AS p95_duration_ms,
       APPROX_QUANTILES((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'size_kb' AND value.int_value >= 0), 100)[OFFSET(95)] AS p95_size_kb
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name IN ('backup_data_success', 'restore_data_success')
GROUP BY event_name, version
ORDER BY event_name, version;
```

```sql
-- 7. Away timeouts, and how many caught a running backup or restore.
SELECT (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'worker_running') AS worker_running,
       COUNT(*) AS events,
       COUNT(DISTINCT user_pseudo_id) AS users
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name = 'session_timeout'
GROUP BY worker_running;
```

`-1` means "could not be read" for `db_size_kb`, `free_space_mb` and `size_kb`. Recipes 4 and 6
drop it inside the parameter sub-select, before any `MIN`, `MAX` or quantile sees it; copy that
pattern into any new query over these three.

## Before the carrying build reaches a testing track

Register the eleven new parameters as custom definitions, as listed in
[firebase-setup.md](firebase-setup.md#custom-definitions). Without that the GA console shows none of
them and only BigQuery keeps their values; the queries above work either way.
