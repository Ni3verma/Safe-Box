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
| `record_form_abandon` | `SingleRecordViewModel.onCleared()` while the form is `NEW` and unsaved | `record_type`, `had_input` | per abandoned create form; `had_input` compares against what the form opened with, so an untouched QR-prefilled authenticator is `false` |

`record_type` values are `RecordType.toAnalyticsValue()`: `login`, `card`, `bank_account`, `note`,
`authenticator`.

### Deliberately not logged

- **`first_record_saved` with time since sign-up.** Redundant: GA4's funnel exploration shows
  elapsed time between steps, and `sign_up → new_*` already is that funnel. Would also have needed
  a persisted "first ever" flag with a migration for existing vaults.
- **A restore click on the empty vault.** `restore_started` already marks a reinstalling or
  migrating user, which is the segment that matters; which button they used does not.
- **A `screen_view` per destination.** Tempting as a general fix, but this is a vault: a stream of
  screen names per session is more telemetry than the question needs. Add a step when there is a
  question only that step answers.

## Reading it in Firebase

Build one funnel exploration, **open** (users may enter at any step), with elapsed time shown:

```
first_open → sign_up → empty_vault_show → add_record_type_pick → [new_login OR new_card OR new_bank_account OR new_note OR new_authenticator]
```

`first_open` and `app_remove` are collected automatically on Android. Then:

1. **Where the drop is** decides the fix. The step with the largest drop is the problem; the table
   below maps it to a cause.
2. **Split `app_remove` by time since `first_open`** (segment on `first_open` ≤ 10 min vs. ≥ 1 day).
   Same-session removes are an expectation or activation problem; later removes are retention,
   which onboarding does not fix.
3. **Segment out restorers** with a `restore_started` filter. They are reinstalls, not activation
   failures, and they skip the whole funnel legitimately.
4. **Check `login_failed` and `update_password_dialog_show` against `sign_up`.** A high ratio means
   users cannot get back in on their second launch (sign-up has no confirm field and masks the
   password), which no empty-state change addresses.

| Largest drop | Likely cause | Lever |
|---|---|---|
| `empty_vault_show → add_record_type_pick` | the empty state does not sell, or users expected autofill/import | empty-state copy and cards; store-listing expectations; import |
| `add_record_type_pick → new_*` with `record_form_abandon.had_input = false` | wrong type, or the form looks like too much work | type descriptions; fewer visible fields |
| same, with `had_input = true` | form friction mid-way | mandatory fields, keyboard flow, save discoverability |
| `new_* → app_remove` days later | no reason to come back | autofill (`origin/feature/auto-fill`), not onboarding |

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
