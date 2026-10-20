# Firebase, Google Analytics and BigQuery

Identifiers, console settings and query recipes for the production analytics property. Everything
here is an identifier or a setting, not a credential. The real `google-services.json`, its API key,
the GPG passphrase that unlocks it in CI and any service-account key stay out of the repo.

## Identifiers

| | Value | Evidence |
|---|---|---|
| Firebase / GCP project ID | `andryoga-safe-box` | committed `app/google-services.json` |
| GCP project number | `510120975864` | Cloud console project picker, 2026-10-20 |
| GA4 property ID | `286937401` | GA Admin → Property details, 2026-10-20 |
| BigQuery dataset | `analytics_286937401` | GA always names the export dataset `analytics_<property id>` |
| Reporting time zone | United Kingdom (`Europe/London`, follows BST) | GA Admin → Property details, 2026-10-20. GA labels it `GMT+00:00` |
| Non-prod Firebase project (qa builds) | `safebox-nonprod` | committed `app/src/qa/google-services.json` |

The project number is embedded in every release APK's `google_app_id`, so recording it exposes
nothing. The `_THIS_IS_DUMMY_` values in the committed `google-services.json` files keep the API
key out of git and make local builds non-reporting; they are not there to hide the number.

**Only CI-built release APKs report to this property.** The committed `app/google-services.json`
and `app/src/debug/google-services.json` carry a dummy `mobilesdk_app_id` (`1:123456789456:…`) and
API key, so the backend rejects local and debug builds; qa builds report to `safebox-nonprod`. The
real file is decrypted from `BASE_64_GOOGLE_SERVICES_JSON` in `release.yml`. Queries therefore need
no `app_info.id` filter. (Read from the three files and the workflow, 2026-10-20.)

The time zone decides the daily table boundary: `event_date` and the `events_YYYYMMDD` split follow
London time, so a "day" of events ends at 05:30 IST in winter and 04:30 IST in summer.

## Console settings

| Setting | Value | Set on |
|---|---|---|
| Event data retention (GA Admin → Data retention) | **14 months**. Default was 2; Explorations cannot look past it, standard reports and BigQuery are unaffected | 2026-10-20 |
| BigQuery link (Firebase → Project settings → Integrations) | Google Analytics **daily** export on, streaming off; Crashlytics, Performance and Sessions also exported | 2026-10-20 |
| Billing | none; the project is in the BigQuery **sandbox** | — |

Sandbox consequences: queries are free, but every table expires **60 days** after creation, so the
export is a rolling two-month window, not an archive. If billing is ever enabled, also raise the
dataset's *default table expiration*; it does not update itself.

The link created `firebase_crashlytics`, `firebase_performance` and `firebase_sessions` at once;
`analytics_286937401` is created by GA's first daily export, about a day later. Nothing before the
link date is exported. (Observed 2026-10-20; confirm the dataset exists and update this line.)

## Custom definitions

GA reports an event parameter only if a custom definition for it existed when the event was
collected. Unregistered parameters never appear in Events cards or Explorations and are **never
backfilled**; they stay visible only in Realtime (30 minutes), on-device logcat and BigQuery. Before
the BigQuery link they were unrecoverable, which is how the `reason` of every
`backup_failure_dialog_show` before 2026-10-20 was lost. So **a new `AnalyticsParam` is registered
before the build reaches a testing track**, and the PR that adds it says so. The form accepts a
parameter name GA has not seen yet.

Registered on 2026-10-20 (GA Admin → Custom definitions), all **event-scoped**, dimension name =
parameter name:

| Parameter | Logged by | Values |
|---|---|---|
| `reason` | `backup_failure_dialog_show`, `backup_data_failure`, `qr_scanner_unsupported_dialog_show` | `BackupFailureReason` names; `TotpUriError` snake case |
| `message` | `backup_data_failure` | exception text; free-form |
| `result` | `backup_select_dir_result`, `clipboard_auto_cleared` | `0`/`1`; `verified`/`unverified` |
| `version` | `restore_data_failure`, `restore_data_wrong_password`, `restore_data_backup_too_new`, `restore_data_backup_empty` | backup format version, `0` if unreadable |
| `is_enabled` | `qr_scanner_torch_toggle` | `0`/`1` |
| `do_not_ask_again` | `notification_permission_rationale_dialog_cancel_click` | `0`/`1` |
| `permission_asked_before` | `notification_permission_rationale_dialog_allow_click` | `0`/`1` |
| `redirect_to_settings` | `notification_permission_rationale_dialog_allow_click` | `0`/`1` |
| `source` | `add_record_type_pick`, `record_open` | `empty_vault`, `add_record_sheet`, `records_list`, `record_detail` |
| `record_type` | `new_*`, `record_open`, `record_delete`, `record_share`, `record_form_abandon`, `field_copy`, `authenticator_copy_click` | lower-case `RecordType` names |
| `had_input` | `record_form_abandon` | `0`/`1` |
| `login_method` | `login_success` | `password`, `biometric`, `password_reset` |
| `login_count` | `login_success` | 1-based ordinal per install; a dimension, **not a metric** ([why](activation-funnel.md#before-the-release-ships-register-the-parameters)) |
| `field` | `field_copy` | lower-case field id, never a value |

Custom **metric**, event-scoped, unit *Standard*:

| Parameter | Logged by | Why a metric |
|---|---|---|
| `count` | `restore_invalid_authenticator_skipped` | the sum is meaningful: total authenticator records silently dropped across all restores |

Quota: 50 event-scoped dimensions and 50 metrics on the free tier; 14 + 1 used.

**Wire formats** (verified from `FA-SVC` logcat on the emulator, 2026-10-17): booleans arrive as
**`0` / `1`**, not `true` / `false`; `Int` params arrive as numbers; `version` is always logged as
a `Double`. A registered dimension is a string, so use it for exact values; audience conditions can
read raw parameters without registration and compare them numerically.

## Where a parameter's value can be seen

| Where | Unregistered params? | Window |
|---|---|---|
| Realtime → *Event count by event name* → event → parameter key | yes | last 30 minutes |
| Local logcat: `adb shell setprop log.tag.FA VERBOSE && adb shell setprop log.tag.FA-SVC VERBOSE && adb logcat -v time -s FA FA-SVC` | yes, and it needs no upload, so it works on debug builds | live, that device |
| DebugView | yes | live, but **only for a build carrying the real `google-services.json`**; debug builds never reach the property |
| Events → event detail cards | registered only | historic |
| Explore → Free form | registered only | historic, within data retention |
| BigQuery | yes | from 2026-10-20, rolling 60 days |

## Query recipes

Run in Cloud console → BigQuery → SQL workspace with project `andryoga-safe-box` selected. In the
Explorer, `analytics_286937401` → `events_(N)` → **Query** opens an editor with the exact table path
pre-filled; replace the date suffix with `*`. The wildcard would also match `events_intraday_*`,
which only exist while streaming export is on (it is off).

Parameters live in the `event_params` array: strings in `value.string_value`, booleans and ints in
`value.int_value`, `version` in `value.double_value`.

```sql
-- Backup failure reasons: the dialog event, and the worker event that also carries the exception.
SELECT
  event_name,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'reason')  AS reason,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'message') AS message,
  COUNT(*) AS n
FROM `andryoga-safe-box.analytics_286937401.events_*`
WHERE event_name IN ('backup_failure_dialog_show', 'backup_data_failure')
GROUP BY event_name, reason, message
ORDER BY event_name, n DESC;
```

```sql
-- Any parameter as a string, whatever its wire type.
(SELECT COALESCE(value.string_value, CAST(value.int_value AS STRING), CAST(value.double_value AS STRING))
 FROM UNNEST(event_params) WHERE key = 'version') AS version
```

Read `NULL` per event and parameter: `message` is only logged by `backup_data_failure`, so it is
always `NULL` for `backup_failure_dialog_show`; a `NULL` `reason` means the event came from a build
that did not log it yet. Add `AND _TABLE_SUFFIX BETWEEN '20261020' AND '20261031'` to bound the
scan to daily tables (an intraday suffix starts with `intraday_`, so it falls outside the range); at
this volume it is a cost of nothing either way.
