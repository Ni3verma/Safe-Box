# 7. Top app bars are owned by each screen's `Scaffold`; the home `NavHost` pins all six transitions

Date: 2026-10-04

Status: Accepted

## Context

Three home-graph bugs were reported together after `v2.2.5.0-rc2` (TOTP). Reproduced on an
emulator against a QA build from the same day and against `v2.1.4.0-rc3` (navigation-compose
2.9.8, Compose BOM 2026.06.01):

| # | Symptom | In rc3? | Root cause |
|---|---|---|---|
| 1 | Type quickly in the records search bar; the caret ends up before the last character | yes, hard to hit | Value-based `TextField` fed from `userInputs → combine → flowOn(default) → stateIn → collectAsState`. Every keystroke round-trips through the ViewModel's `combine`; a stale value arriving mid-composition rewrites the field and drops the selection. TOTP made the `combine` heavier, so the race became easy to hit. |
| 2 | Back swipe from a record detail (gesture navigation), released: the screen scales down toward the centre with the list visible behind it | **no** | navigation-compose **2.10.0** (`#233`, `4fd0d92`, 2026-09-11) added `DefaultNavTransitions` with `predictivePopExitTransition = scaleOut(0.7f)` and `predictivePopEnterTransition = fadeIn(spring)`. `HomeScreen` only pinned the four button-driven slots, so a back swipe used the new library defaults while the toolbar's back arrow (`popBackStack`) still crossfaded over 700 ms. |
| 3 | Start a back gesture on the detail screen and cancel it: the detail content stays but the **home search bar** replaces the detail top bar | yes | The bar was a single `MainViewModel.topBarState` written by each screen from an `OnStart` lifecycle hook. `NavHostEventHandler.onBackStarted → prepareForTransition` moves the previous entry to `STARTED`, which fires `ON_START` on the records screen and overwrites the shared state mid-gesture; cancelling never re-fires `ON_START` on the detail entry, so the bar is never restored. |

Bug 3 is structural: any top bar that lives *outside* the `NavHost` content cannot follow a
predictive back gesture, because the gesture animates destination content and the bar is not part
of it. Bug 2 is a reminder that unpinned transition slots change meaning whenever the library's
defaults do.

## Decision

1. **Each destination composes its own `Scaffold` with its own top app bar.** The outer `HomeScreen`
   scaffold owns only what is shared — the bottom navigation bar and the global `SnackbarHost` — and
   passes no window insets down (`contentWindowInsets = WindowInsets(0)`, `NavHost` consumes the
   bottom-bar padding). The bar is therefore part of destination content and animates, including
   under a scrubbed or cancelled predictive back, with its screen. `MainViewModel` no longer knows
   about top bars; `TopBarState`, `TopAppBarConfig`, `getBasicTopAppBarConfig` and the `OnStart`
   helper are gone. `MyAppTopAppBar` is a thin slot-based wrapper that keeps the app's colours and
   scroll behaviour in one place.
2. **The home `NavHost` pins all six transition slots** (`enter`, `exit`, `popEnter`, `popExit`,
   `predictivePopEnter`, `predictivePopExit`) to the same 700 ms crossfade, so gesture back and
   button back cannot drift apart again when the library's defaults change.
3. **The search field is a `TextFieldState` owned by `RecordsViewModel`.** The UI writes into it
   directly; the ViewModel observes it with `snapshotFlow` and feeds that into the existing
   `combine`. No value round-trips through the data pipeline, so the caret cannot be reset by a
   late emission.

Evidence that it holds (debug build vs. the 2026-10-03 QA control build, same emulator, gesture
navigation): typing `abcdefghijklmnopqrstuvwxyz0123456789` with `adb shell input text` produced
`abcdefghimnopqrstuvwxyz0123456789j` on the control and the exact string on the fix; a cancelled
gesture left the control showing detail content under the home search bar and the fix showing
`← Login`; a committed gesture scaled the control's detail content toward the centre while the fix
crossfaded. `RecordsViewModelTest` (34) and `RecordsSearchBarTest` (5, including a caret-at-end
assertion after appending text) cover the search path on the JVM and on device.

## Alternatives rejected

| Option | Why not |
|---|---|
| Keep the shared bar but key it by `NavBackStackEntry.id` so a cancelled gesture can restore it | Fixes the stale-state symptom only. The bar still sits outside the `NavHost`, so it cannot scrub with the gesture, and with the 2.10 defaults the screen would scale down under a static bar — which is exactly what looked broken in bug 2. |
| Pin only `predictivePopExit`/`predictivePopEnter` and keep the shared bar | Same objection; also leaves bug 3 unfixed because `prepareForTransition` fires `ON_START` regardless of which animation is used. |
| Adopt the platform-style scale-down for gesture back instead of pinning it to the crossfade | Viable now that every screen is opaque with its own bar, but the owner approved the rc3 look. Switching later is a two-line change: drop the two `predictivePop*` pins in `HomeScreen.kt`. |
| Module-wide `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api` compiler flag | Would hide which call sites depend on experimental API. `MyAppTopAppBar` and `RecordsTopAppBar` expose `TopAppBarScrollBehavior` in their signatures, so they carry `@ExperimentalMaterial3Api` and each caller opts in locally. |
| Debounce or `distinctUntilChanged` the search value flow | Treats the symptom; any asynchronous write-back into a value-based `TextField` can still land between two keystrokes. `TextFieldState` is the Compose-recommended model for exactly this reason. |

## Consequences

- A new destination in the home graph must compose its own `Scaffold` and top bar; there is no
  shared bar to configure. The QR scanner is the one full-bleed screen and applies
  `statusBarsPadding()` / `navigationBarsPadding()` itself.
- Search text survives bottom-nav switches because it lives in `RecordsViewModel`, unchanged from
  before.
- JVM tests that drive a `TextFieldState` must call `Snapshot.sendApplyNotifications()` after
  mutating it, or `snapshotFlow` never emits — nothing on the JVM sends global-snapshot apply
  notifications. See [testing-strategy.md](../testing/testing-strategy.md#principles).
- Future Dependabot bumps of `navigation-compose` cannot silently change the home graph's back
  animation; a change there is now a deliberate edit to `HomeScreen.kt`.
