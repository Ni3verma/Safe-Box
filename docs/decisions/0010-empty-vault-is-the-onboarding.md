# 10. The empty vault is the onboarding; no intro carousel

Date: 2026-10-17

Status: Accepted

## Context

Firebase showed many more `sign_up` events than `new_*` (first record) events, and a high
`app_remove` count. The owner's first instinct was a show-and-tell onboarding carousel after
sign-up, to raise the chance that a user who signed up goes on to use the app.

What a new user actually met after sign-up, read from the code on 2026-10-17:

- `SignupScreen`: master password (five rules) plus hint, then straight into Home.
- `RecordsScreen`'s empty state: an **error icon** (`Icons.Outlined.ErrorOutline`), one sentence,
  and two equal-weight filled buttons, "Add a new record" and "Restore data". For a first-time user
  the restore button is noise and the icon says something went wrong.
- "Add a new record" opened a bottom sheet of five bare type names, then a form.
- Nothing was logged between `sign_up` and the save, so the drop could not be located.

## Decision

1. **No intro carousel.** The user installed a "Safe Box"; they know what it is for. Slides delay
   value, are skipped at high rates in utility apps, add friction before the step that already
   converts (sign-up) and nothing at the step that loses users (the first record).
2. **The empty state is the onboarding.** It leads with the action: a title, one line of value,
   and one card per record type with a sentence on what it holds. Each card opens that type's
   create form directly, skipping the sheet. Restore stays, as a text button under the intro.
3. **Instrument before investing further.** Four events fill the gap
   (`empty_vault_show`, `add_record_sheet_show`, `add_record_type_pick`, `record_form_abandon`);
   their meaning and the reading guide are in
   [activation-funnel.md](../analytics/activation-funnel.md). The next lever — a setup checklist
   card, CSV import, or autofill — is chosen on that data, not guessed.

## Why

- Different leak points need different fixes, and three are plausible: never tapping add (value or
  expectation), abandoning the form (friction), or saving then uninstalling days later (no reason
  to return). A carousel helps with none of them; the last one is only answered by autofill, which
  already has a branch (`origin/feature/auto-fill`).
- The empty-state redesign is no-regret: it costs under a day, removes an error icon from the
  first screen, and halves the taps to a form. It is worth shipping before the data exists.
- Showing the type descriptions *as* the choice teaches the record types without a separate
  screen, which is where a carousel would have put them.

## Evidence

- `AnalyticsKey.kt` before this change: `SIGN_UP` and `NEW_*` with nothing in between; no
  `screen_view` or `setCurrentScreen` anywhere in `app/src/main` (grep, 2026-10-17). Being a
  single-Activity app, Firebase's automatic `screen_view` cannot distinguish screens.
- `RecordsScreen.kt` `EmptyVaultBody` used `Icons.Outlined.ErrorOutline` and two `Button`s.
- The upgrade-test harness (`SafeBoxApp.restoreIntoEmptyVault`) taps the restore button by label
  with no scrolling, which is why the restore button sits above the cards: it must be on screen on
  a Pixel 8 AVD without a scroll.

## Consequences

- Any carousel, tooltip tour or coach-mark proposal should start from the funnel data, not from
  the symptom. If `empty_vault_show → add_record_type_pick` is not the largest drop, the empty
  state is not the problem.
- The record-type descriptions (`record_type_desc_*`) are now the only in-app explanation of the
  types; keep them concrete when editing.
- Removing `new_record_button` changed the marker the E2E suite used to detect the empty state; it
  is now `empty_vault_title`.
- Follow-ups, in the order the data is expected to justify them: one line on the sign-up screen
  explaining that the master password is unrecoverable without the device lock; a setup checklist
  card on the records list; import from a Google Password Manager CSV; autofill.
