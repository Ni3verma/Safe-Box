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
| `DURATION_MEDIUM_MS` | 300 ms | every screen or content transition, the bottom bar, the odometer roll |
| `DURATION_OUTGOING_MS` | 90 ms | outgoing half of a fade through |
| `DURATION_INCOMING_MS` | 210 ms | incoming half of a fade through (starts after the outgoing half) |
| `FADE_THROUGH_INITIAL_SCALE` | 0.92 | incoming content of a fade through grows from here |
| `POP_IN_INITIAL_SCALE` | 0.6 | glyphs and buttons that pop in start from here |

## Vocabulary

| Pattern | Helper | Meaning | Where |
|---|---|---|---|
| **Fade through** | `fadeThroughEnter/Exit`, `fadeThrough()` | Outgoing content clears the stage first, then the incoming content fades (and slightly scales) in. Content is unrelated. | Root `NavHost` hops (Login ↔ Home), bottom-nav tab switches, records body (loading / empty / list), detail field view ↔ edit (no scale), backup dialog body |
| **Fade over** | `fadeOverEnter()` + `ExitTransition.KeepUntilTransitionsFinished` | New screen fades in on top of the old one, which is held opaque underneath. Only for screens that paint the same background, where a fade through would dip to a blank frame. | Loading → Login / Signup |
| **Shared axis X** | `HomeNavTransitions.kt` | Hierarchical push/pop, predictive-back aware. | Records → record detail, QR scanner |
| **Entrance** | `rememberEntranceState`, `entranceSlideIn`, `entrancePopIn` | One-shot choreography when a screen first appears: elements settle in order with a 100 ms stagger. Saved across rotation so it never replays. | `AuthScreenLayout` (greeting from above, card from below, lock pops in) |
| **Pop-in swap** | `popInSwap()` | Incoming glyph overshoots in (EaseOutBack) while the outgoing one fades; for single icons where a crossfade reads as a flicker. | Backup/restore dialog status icon |
| **Reject shake** | `rememberRejectShake(counter)` | Horizontal decaying shake plus `HapticFeedbackType.Reject`. Driven by a counter so it replays on every rejection. | Login password field, backup/restore password field |
| **Odometer roll** | `RollingText` | Old value slides up and out, new one slides in from below, clipped to the line. | TOTP code in the row badge and the detail field |
| **Glyph swap** | `CopyIcon` + `rememberCopiedFlag` | Copy icon becomes a check mark with a spring pop, reverts after 1.5 s. Single semantics node by design. | TOTP copy buttons |
| **Reveal** | `AnimatedVisibility` with `expandVertically` / `scaleIn` | Secondary UI appears in place rather than popping. | Login hint, detail action row, detail save button, detail rows that exist in one mode only (dates, authenticator seed), filter-chip check |
| **Reflow** | `Modifier.animateItem(fadeInSpec = incomingSpec(), fadeOutSpec = outgoingSpec())`, `animateContentSize()` | Lists and chips glide to their new positions instead of jumping. Item fades use fade-through timing so departing rows clear out before arriving ones fade in. | Records list on filter / search changes, filter chips |
| **Scan line** | infinite transition in `QrScannerScreen` | A soft glow sweeps the viewfinder cutout (2.2 s, reversing) so the camera reads as actively looking. | QR scanner |

### Rules of thumb

- A transition between **unrelated** content is a fade through. Between **the same element in a new
  state** (a field switching to edit mode, a dialog body) it is a fade through without scale, so
  the element stays anchored.
- Two screens that share a background never fade through each other; use the fade over.
- A rejected input shakes the *offending field*, never the whole screen, and always pairs with the
  reject haptic.
- Nothing delays navigation: no success animation runs between a confirmed login and Home.
- Entrance choreography is for screens the user sees cold (auth). Home-graph screens arrive via
  navigation transitions and do not add a second entrance on top.
- Content that collapses away must **still have content while it collapses**. `shrinkVertically`
  animates the measured size, so a row whose children are removed in the same recomposition is
  already 0 dp tall when the exit starts and simply vanishes. `SingleRecordScreen` therefore keeps
  drawing a row in the mode it last had content for (`rowRenderMode`) until the shrink finishes.
- A `Dialog` is its own window: it is not faded by the `NavHost` transition of the screen that
  hosts it and stays up, scrim included, until that screen leaves composition. A dialog whose
  button navigates away must drop itself first (`UserAwayDialog`).
- Inside a `Box` nested in a `Column`, `AnimatedVisibility` resolves to the `ColumnScope` overload
  and fails to compile ("cannot be called in this context with an implicit receiver") because of
  the layout DSL marker. Keep the `AnimatedVisibility` directly in the column instead of wrapping
  it; scale and alpha transitions do not change layout size, so no slot reservation is needed.

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

- The system splash screen's exit animation is also scaled, and the auth entrance can start while
  the splash still covers the activity. To see the entrance in isolation, trigger it without a
  cold start: set the away timeout to its minimum in Settings, background the app until the
  "you were away" dialog appears, and confirm it (Loading → Login, no splash).
- Gboard shows a floating toolbar in frames when a hardware keyboard is attached;
  `adb shell settings put secure show_ime_with_hard_keyboard 0` hides it.
- `delay()`-based timers (the copy check's 1.5 s revert, the dialog shake's wait) are *not*
  scaled, so at high scales they fire mid-animation.
- With a fingerprint enrolled on the AVD the Login screen raises the system biometric prompt as
  soon as it composes, and the prompt's scrim dims the entrance choreography in every frame after
  the first ~300 ms. Either read the entrance from the top third of the frame (greeting and lock
  glyph stay visible above the prompt) or capture it on the Signup screen of a freshly cleared
  install, which has no prompt.
