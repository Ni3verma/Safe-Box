# Post-signup activation funnel

What is measured between a user's sign-up and their first saved record, why each event exists,
and how to read the result in Firebase. The decision that shaped the empty vault is
[ADR-0010](../decisions/0010-empty-vault-is-the-onboarding.md).

## Why explicit events

Safe-Box is one Activity with a Compose `NavHost`, so Firebase's automatic `screen_view` reports a
single screen (`MainActivity`) for the whole app. Every screen-level step in a funnel therefore has
to be an explicit event in `AnalyticsKey`, or it does not exist. Verified 2026-10-17: there is no
`logScreenView` / `setCurrentScreen` call anywhere in `app/src/main`, and the Compose `NavHost`
never reports destinations to Firebase.

The symptom that started this (2026-10-17): `sign_up` events far exceed the sum of `new_*`
events, and `app_remove` is high. With only those events the gap is a black box.

## The events

In funnel order. Bounds matter: a funnel in GA4 counts users, but event counts are read too.

| Event | Fired by | Params | Bound |
|---|---|---|---|
| `sign_up` | `SignupViewModel` | — | once per install (pre-existing) |
| `empty_vault_show` | `RecordsViewModel` on `OnEmptyVaultShown` from `EmptyVaultBody` | — | once per `RecordsViewModel`, i.e. per unlock session; a backed-out form is not a second show |
| `add_record_sheet_show` | `RecordsViewModel`, sheet opened from the list's add button | — | per open; the empty vault never opens the sheet |
| `add_record_type_pick` | `RecordsViewModel` on `OnAddNewRecord` | `record_type`, `source` ∈ {`add_record_sheet`, `empty_vault`} | per pick |
| `new_login` … `new_authenticator` | the `*DataRepositoryImpl.upsert*` on insert | — | per saved record (pre-existing). Restore writes through the DAOs, so it does not inflate these |
| `record_form_abandon` | `SingleRecordViewModel.onCleared()` while the form is `NEW` and unsaved | `record_type`, `had_input` | per abandoned create form; `had_input` compares against what the form opened with, so an untouched QR-prefilled authenticator is `0` |

`record_type` values are `RecordType.toAnalyticsValue()`: `login`, `card`, `bank_account`, `note`,
`authenticator`.

## Retention and value

The funnel above ends at "saved one record". Whether the app earned its place is a different
question: did the user come back, and did a saved record ever get *used*?

| Event | Fired by | Params | Bound |
|---|---|---|---|
| `login_success` | `LoginViewModel.onAuthSuccess`, the single exit of all three unlock paths | `login_method` ∈ {`password`, `biometric`, `password_reset`}, `login_count` (1-based ordinal of this login for the install) | per unlock. Sign-up itself is not a login, so the first `login_success` is the first *return* |
| `record_open` | `RecordsViewModel` on `OnRecordClick` | `record_type` | per open |
| `field_copy` | `SingleRecordViewModel` on `OnFieldCopied` from `RowField` | `record_type`, `field` (a `FieldId` name, e.g. `login_password`; never a value) | per copy. Authenticator codes stay on `authenticator_copy_click` |
| `record_share` | `SingleRecordViewModel` | `record_type` | per share |
| `record_delete` | `SingleRecordViewModel` | `record_type` | per delete |
| `search_used` | `RecordsViewModel`, first non-blank query | — | once per `RecordsViewModel`, i.e. per unlock session |

`login_count` exists so that retention needs no BigQuery: an audience of users whose
`login_success` has `login_count >= 3` is the returning cohort, and `login_failed` without a
following `login_success` in the same session is a lockout. `login_method = password_reset` is the
forgot-password recovery and is kept apart from `password` on purpose.

### Deliberately not logged

- **`first_record_saved` with time since sign-up.** Redundant: GA4's funnel exploration shows
  elapsed time between steps, and `sign_up → new_*` already is that funnel. Would also have needed
  a persisted "first ever" flag with a migration for existing vaults.
- **A restore click on the empty vault.** `restore_started` already marks a reinstalling or
  migrating user, which is the segment that matters; which button they used does not.
- **`signup_abandon` with the last password-validator state.** Attractive for the "are the
  password rules the friction" question, but it cannot be measured honestly: `onCleared` fires on
  an explicit back-press exit, not when the user presses Home and the process is later killed,
  which is how most abandons happen. Per-keystroke validator states are noise, because every user
  passes through invalid states while typing a valid password. `first_open → sign_up` is the
  measure; if that step ever becomes the largest drop, the fix is a UX review of the rules, not
  more telemetry.
- **A `screen_view` per destination.** Tempting as a general fix, but this is a vault: a stream of
  screen names per session is more telemetry than the question needs. Add a step when there is a
  question only that step answers.

## Reading it in Firebase

### Before the release ships: register the parameters

GA4 does not expose event parameters in Explorations until each is registered as a custom
definition (Admin → Custom definitions), and registration is **not retroactive**: data collected
before it has no value for that dimension. Register these the day the build goes to a testing
track, not when the first question comes up:

| Parameter | Register as | Used for |
|---|---|---|
| `source` | event-scoped dimension | which entry point the pick came from |
| `record_type` | event-scoped dimension | breakdown of every record event |
| `had_input` | event-scoped dimension | typed vs. untouched abandons |
| `login_method` | event-scoped dimension | password / biometric / password_reset |
| `field` | event-scoped dimension | which field gets copied |
| `login_count` | event-scoped dimension | exact-ordinal filters, e.g. a funnel step on `login_count = 2` for second-login retention. **Not a metric**: it is an ordinal, so a sum is `1 + 2 + 3`, not three logins. Average logins per user is `login_success` event count ÷ active users, both built in |

Audience conditions can read raw parameters without registration, and compare them numerically
(`login_count >= 3`); a registered dimension is a string, so use it for exact values only.
Funnel breakdowns and tables cannot use unregistered parameters at all. Six definitions, against a
free-tier quota of 50 event-scoped dimensions.

**Wire formats** (verified from `FA-SVC` logcat on the emulator, 2026-10-17): booleans arrive as
**`0` / `1`**, not `true` / `false`, so a `had_input` breakdown has values `0` and `1`; `Int` params
arrive as numbers (`login_count=1`). This holds for the older boolean params too
(`do_not_ask_again`, `is_enabled`).

### The explorations

| Exploration | Type | Definition | Question it answers |
|---|---|---|---|
| Activation | Funnel, **closed**, elapsed time on | `first_open` → `sign_up` → `empty_vault_show` → `add_record_type_pick` → [`new_login` OR `new_card` OR `new_bank_account` OR `new_note` OR `new_authenticator`] | where new installs drop, and how long each step takes |
| Empty-vault conversion | Funnel, closed | `empty_vault_show` → `add_record_type_pick` where `source = empty_vault` | how many who saw the cards used one. A `source` breakdown on the activation funnel cannot answer this: users who never picked carry no `source`, and GA4 assigns breakdown values from the first step that has one |
| Sheet conversion | Funnel, closed | `add_record_sheet_show` → `add_record_type_pick` where `source = add_record_sheet` | same for the sheet; compare the two step rates, not the raw counts |
| Form friction | Funnel, closed | `add_record_type_pick` → `new_*`; compare with a free-form table of `record_form_abandon` by `record_type` × `had_input` | is the form the problem, and for which type |
| Early removes | User segment, sequence | `first_open` → is followed by `app_remove`, **within 30 minutes** | expectation mismatch (same session) vs. retention (later) |
| Returned, still empty | User segment, sequence | `login_success` is followed by `empty_vault_show`; **exclude** users with any `new_*`. The order matters: a user who saw the empty vault on day one, saved, and returned later also has both events | came back and found the vault still empty, and never saved: value, not bounce |
| Returning cohort | Audience | `login_success` with `login_count >= 3` | retention without BigQuery |

Why **closed**: step 1 is `first_open`, so in a closed funnel the population is exactly the users
who installed in the date range. An open funnel would also count long-time users' `record_open`
and `new_*` events and bury the new-user drop.

Apply a segment that **excludes** users with `restore_started` to every activation view:
reinstalls skip the whole funnel legitimately (`allowBackup` is false, so a reinstall is a new
Firebase user and a new `first_open`).

### Reading the result

1. **The largest drop decides the fix**; the table below maps it to a cause.
2. **The `empty_vault_show → add_record_type_pick` drop is ambiguous on its own**: a user who
   looked for five seconds and left and one who read every card and left count the same. Split
   it with engagement time (the automatic `user_engagement` event): sessions with
   `empty_vault_show`, no pick, and under ~15 s of engagement are bounces, which is a
   store-listing or expectation problem; longer ones are the empty state failing to convince.
3. **For `record_type = authenticator` the step after the pick is the scanner, not the form.**
   Its drop is read from the existing scanner events (`qr_scanner_show`,
   `qr_scanner_success` / `qr_scanner_manual_click`, `qr_scanner_cancel`, `camera_perm_result`),
   and only then from `record_form_abandon`.
4. **`record_form_abandon` also fires when the away timeout pops the home graph** while a create
   form is open. Expect a small floor of abandons with `had_input = 1` that are not form friction;
   the away timeout default is in `SettingsDataStore`.
5. **Check `login_failed` and `update_password_dialog_show` against `sign_up`.** A high ratio means
   users cannot get back in on their second launch (sign-up has no confirm field and masks the
   password), which no empty-state change addresses.

| Largest drop | Likely cause | Lever |
|---|---|---|
| `first_open → sign_up` | the master-password rules, or no idea what the app is for | sign-up copy; rule review |
| `empty_vault_show → add_record_type_pick`, short engagement | expected autofill or import; store listing oversold | store-listing expectations; import |
| same, long engagement | the empty state does not convince | empty-state copy and cards |
| `add_record_type_pick → new_*` with `had_input = 0` | wrong type, or the form looks like too much work | type descriptions; fewer visible fields |
| same, with `had_input = 1` | form friction mid-way | mandatory fields, keyboard flow, save discoverability |
| `new_* → app_remove` days later | no reason to come back | autofill (`origin/feature/auto-fill`), not onboarding |
| `new_*` saved but no later `record_open` / `field_copy` | records are stored, never retrieved: the app is a notebook, not a tool | autofill; copy affordances |
| few `login_success` with `login_count >= 2` | users do not return after the first session | same as above; also check `login_failed` for lockouts |

Give a change two weeks of data before judging it; sign-up volume is low enough that a week is
noise.

## Adding a step

- Add the key to `AnalyticsKey` with a comment stating its bound, and params to `AnalyticsParam`.
- Log from the ViewModel, never from a composable; UI reports an action, the ViewModel decides
  whether it is an event. A `*_SHOW` that re-fires on recomposition needs a guard
  (`isEmptyVaultShowLogged` is the template).
- Add a ViewModel unit test asserting the event and its params (`capturedParams` in
  `RecordsViewModelTest`, `FakeAnalyticsHelper` in `SingleRecordViewModelTest`).
- Update the table above.
