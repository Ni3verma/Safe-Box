# Motion

How Safe-Box moves, and why each pattern was chosen. Everything here is built from the helpers
in `app/src/main/java/com/andryoga/safebox/ui/core/motion/`; screens never hard-code durations
or easing. Navigation-level motion for the home graph is decided in
[ADR 0007](../decisions/0007-screen-owned-top-app-bars.md); this page covers the vocabulary and
the in-screen animations layered on top of it.

## Tokens

`MotionTokens` (`MaterialMotion.kt`) holds the only durations and scales in the app:

| Token | Value | Used for |
|---|---|---|
| `DURATION_MEDIUM_MS` | 300 ms | every screen or content transition and the bottom bar |
| `DURATION_OUTGOING_MS` | 90 ms | outgoing half of a fade through |
| `DURATION_INCOMING_MS` | 210 ms | incoming half of a fade through (starts after the outgoing half) |
| `FADE_THROUGH_INITIAL_SCALE` | 0.92 | incoming content of a fade through grows from here |
| `POP_IN_INITIAL_SCALE` | 0.6 | glyphs and buttons that pop in start from here |

## Vocabulary

| Pattern | Helper | Meaning | Where |
|---|---|---|---|
| **Fade through** | `fadeThroughEnter/Exit`, `fadeThrough()` | Outgoing content clears the stage first, then the incoming content fades (and slightly scales) in. Content is unrelated. | Bottom-nav tab switches, records body (loading / empty / list), detail field view ↔ edit (no scale) |
| **Shared axis X** | `HomeNavTransitions.kt` | Hierarchical push/pop, predictive-back aware. | Records → record detail, QR scanner |
| **Reject shake** | `Modifier.rejectShake(counter)` | Horizontal decaying shake plus `HapticFeedbackType.Reject`. Driven by a counter so it replays on every rejection, and never for the counter value the screen was created with (rotation). | Login password field (`LoginUiState.failedLoginAttempts`) |
| **Glyph swap** | `CopyIcon` + `rememberCopiedFlag` | Copy icon becomes a check mark with a spring pop and reverts after 1.5 s. Single semantics node by design; the check state is exposed as a `stateDescription` ("Copied") for screen readers and tests. | TOTP copy buttons |
| **Reveal** | `AnimatedVisibility` with `expandVertically` / `scaleIn` | Secondary UI appears in place rather than popping. | Login hint, detail action row, detail save button, detail rows that exist in one mode only (dates, authenticator seed), filter-chip check |
| **Reflow** | `Modifier.animateItem(fadeInSpec = incomingSpec(), fadeOutSpec = outgoingSpec())`, `animateContentSize()` | Lists and chips glide to their new positions instead of jumping. Item fades use fade-through timing so departing rows clear out before arriving ones fade in. | Records list on filter / search changes, filter chips |

Patterns that were prototyped alongside these and dropped by product decision (2026-10-06): root
`NavHost` fade through / fade over, Login / Signup entrance choreography, odometer-style TOTP
digits, backup/restore dialog motion, QR scan line. The working prototype is the first commit of
`feat/motion-polish` (`af6b265`) should any of them be wanted later.

### Rules of thumb

- A transition between **unrelated** content is a fade through. Between **the same element in a new
  state** (a field switching to edit mode) it is a fade through without scale, so the element stays
  anchored.
- A rejected input shakes the *offending field*, never the whole screen, and always pairs with the
  reject haptic.
- Nothing delays navigation: no success animation runs between a confirmed login and Home.
- Content that collapses away must **still have content while it collapses**. `shrinkVertically`
  animates the measured size, so a row whose children are removed in the same recomposition is
  already 0 dp tall when the exit starts and simply vanishes. `SingleRecordScreen` therefore keeps
  drawing a row in the mode it last had content for (`rowRenderMode`) until the shrink finishes.
- A `Dialog` is its own window: it is not faded by the `NavHost` transition of the screen that
  hosts it and stays up, scrim included, until that screen leaves composition. `UserAwayDialog`
  currently shows this (its scrim outlives the Home → Login hop); a dialog whose button navigates
  away should drop itself first if that is ever addressed.
- Inside a `Box` nested in a `Column`, `AnimatedVisibility` resolves to the `ColumnScope` overload
  and fails to compile ("cannot be called in this context with an implicit receiver") because of
  the layout DSL marker. Keep the `AnimatedVisibility` directly in the column instead of wrapping
  it; scale and alpha transitions do not change layout size, so no slot reservation is needed.

## Testing motion

Compose UI tests auto-advance the frame clock, so by the time an assertion runs every
`AnimatedVisibility` / `AnimatedContent` has finished and outgoing content is gone; assert on the
end state and on *counts* (`onAllNodesWithText(...).assertCountEquals(1)`) to catch content that a
transition failed to remove. `delay()`-based timers inside composition (the copy check's revert)
run on the same test scheduler: `mainClock.advanceTimeBy(ms)` fires them. Haptics are asserted by
substituting `LocalHapticFeedback` with `FakeHapticFeedback` (androidTest `test/fakes`). Examples:
`RejectShakeTest`, `LoginScreenTest`, `TotpBadgeTest`, `RecordsScreenTest`, `SingleRecordScreenTest`.

## Verifying on the emulator

Compose honours `animator_duration_scale`, so a 300 ms transition at scale 6 spans ~1.8 s and can
be captured as a frame burst. `adb exec-out screencap` returns black for this app (`FLAG_SECURE`);
use the emulator console instead:

```bash
adb shell settings put global animator_duration_scale 6
adb -e emu screenrecord screenshot /tmp/frames      # one frame, ~120 ms per call
adb -e emu screenrecord start --time-limit 30 /tmp/demo.webm   # real-time video; `stop` to end
adb shell settings put global animator_duration_scale 1.0
```

Traps:

- Gboard shows a floating toolbar in frames when a hardware keyboard is attached;
  `adb shell settings put secure show_ime_with_hard_keyboard 0` hides it.
- `delay()`-based timers (the copy check's 1.5 s revert) are *not* scaled, so at high scales they
  fire mid-animation.
