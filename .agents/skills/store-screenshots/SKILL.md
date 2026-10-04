---
name: store-screenshots
description: How to regenerate the Play Store listing images and the README grid from the real app on a dedicated emulator, re-render after a copy or theme edit, and the traps in doing either
---

# Store screenshots

Use this when the listing images need refreshing after a UI change, when the copy or the look of
the images changes, or when a scene is added or dropped. The images are **generated**: never edit
`screenshots/readme/*.png` or the README block by hand; both are overwritten on the next run.

Design, formats and the decisions behind them: [docs/store-listing.md](../../../docs/store-listing.md)
and [ADR-0006](../../../docs/decisions/0006-store-screenshots-from-emulator-captures.md).

## What a run produces

| Path | Committed | What |
|---|---|---|
| `screenshots/raw/<capture>-<theme>.png` | no (gitignored) | 1080×2400 emulator captures, one per capture per appearance, plus `-<seed>` palette variants |
| `screenshots/readme/NN-<scene>.png` | **yes** | the store images, 1080×1920 8-bit RGB, in store order |
| `README.md` block between `<!-- store-screenshots:start/end -->` | **yes** | the grid of those images |
| `screenshots/preview.html` | no | review page: every store image beside the captures that made it |
| `upgrade-test-out/store-screenshots/` | no | emulator log, logcat, instrumentation output, Chrome pages |

The capture side is `scripts/take-store-screenshots.sh` with `scripts/lib/store-avd.sh` and the
`store.StoreScreenshotTour` class in `:upgrade-test`; the compositing side is
`scripts/store-screenshots/render.py` with `scenes.json` (what each image shows) and `theme.json`
(how every image looks).

## Full run

Needs: the Android SDK with the API 35 Google Play system image for this machine's ABI
(`system-images;android-35;google_apis_playstore;<abi>`), Google Chrome, `python3` (standard
library only) and the environment from the build-and-test skill. Everything here that touches
Gradle, adb, the emulator or Chrome has to run **outside the agent sandbox**.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"   # one adb only; see build-and-test "Two adb binaries"
./gradlew :app:assembleQa -PappLabel="Safe Box" :upgrade-test:assembleDebug
scripts/take-store-screenshots.sh
```

- `-PappLabel="Safe Box"` matters: the biometric sheet prints the app's label, and the qa build is
  otherwise "Safe Box QA". The qa `app_name` is a `resValue` fed by that property (`app/build.gradle`).
  The script warns when the label it reads from the APK contains `QA` or `DEBUG`.
- The **first run** creates the AVD `SafeBox_Store_Pixel_8_API_35` (no hardware keyboard, 6 GB
  data, PIN `1234`), boots it cold and enrols a fingerprint through Settings' own flow: several
  minutes. Later runs restore the snapshot in seconds. The script only ever matches that AVD name;
  a development emulator running alongside is never touched.
- Then: sign-up and demo vault ≈ 1 min, two capture passes (light, dark) with the four palette
  variants ≈ 3 min, compositing ≈ 10 s. The emulator is stopped at exit unless `--keep-emulator`.
- Sanity check at the end: `git status` shows only the `NN-*.png` that changed, and
  `screenshots/preview.html` opens to the eight images.

Timings and the AVD details were measured on 2026-10-04 on an M-series Mac.

## Re-render without an emulator

After editing `scenes.json` (copy, order, which captures) or `theme.json` (colours, font, layouts):

```bash
scripts/take-store-screenshots.sh --render-only              # from screenshots/raw, rewrites README block
scripts/take-store-screenshots.sh --render-only --render-theme path/to/other-theme.json
```

The captures in `screenshots/raw/` are the input; they are not committed, so a fresh clone needs a
full run first. `python3 scripts/store-screenshots/render.py --help` lists the compositor's own
options (`--only scene`, `--out`, `--readme -`), which the wrapper does not expose.

## Recapture a subset

`--scenes <ids>` and `--theme light|dark` narrow the capture passes (ids are in
`upgrade-test/.../store/Scene.kt`). `screenshots/raw/` is cumulative, so recapturing one scene and
rendering reuses the other captures from the previous run:

```bash
scripts/take-store-screenshots.sh --scenes backup --theme light
```

Any `--out` other than `screenshots` is a **trial**: its images and review page land under that
directory and the README is left alone. `--no-render` stops after the captures (probe runs).

## Use your own screenshots

The compositor does not know or care whether a capture came from the tour. A PNG you took yourself
is a first-class input, and the usual reason to want one is a scene the tour cannot reach, or a
scene whose tour step broke after a UI change while the others still work.

1. The file must be **exactly 1080×2400**, the Pixel 8 frame's display. Pixel 8 / 7a / 6a
   screenshots are. Anything else is refused (`… is 1080x2340; the pixel_8 frame takes 1080x2400
   captures`); do not resize, add a frame for that device instead (Android Studio device art into
   `scripts/store-screenshots/frames/<name>/`, then `"frame"` in `theme.json`).
2. For the same status bar as the tour's captures, put the phone in SystemUI demo mode first:
   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e level 4 -e datatype none
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   # take the screenshot, then:
   adb shell am broadcast -a com.android.systemui.demo -e command exit
   ```
3. Drop it in as `screenshots/raw/<name>.png` (`[A-Za-z0-9_-]+`; the `-light` / `-dark` suffix is a
   convention, not a rule) and name it in a scene's `captures` in `scenes.json`.
4. `scripts/take-store-screenshots.sh --render-only`.

Mixing is fine: `--scenes` recaptures the scenes the tour still handles, hand-made files fill the
rest, one render composites all eight.

Two limits. `screenshots/raw/` is **gitignored**, so a hand-made capture lives on your machine only;
a fresh clone renders `scene x needs x.png, which does not exist` until the file is put back. And
the biometric sheet cannot be screenshotted on a phone at all (`FLAG_SECURE` gives a black image);
only the emulator-console path in the script sees it. For a one-off image outside the listing, the
2–8 scene rule still applies to any scenes file: add the scene to the committed file and render it
alone with `python3 scripts/store-screenshots/render.py --only <id>`, which holds the README and
preview back until every scene exists.

## Change what the images say or look like

| Want | Edit |
|---|---|
| Headline, subhead, order, which captures an image uses | `scripts/store-screenshots/scenes.json` — ids `[a-z0-9_]`, `\n` is a manual line break, store order is file order, 2–8 scenes |
| Colours, gradient, font, text sizes, phone shadow | `scripts/store-screenshots/theme.json` — the Inter variable font ships in `fonts/`; add another file there and name it in `font.files` |
| Phone positions | `theme.json` `layouts`: slots with `left`/`top` (px on the 1080×1920 canvas), `scale`, `rotate` (deg), optional `clip` (polygon in % of the footprint; `split` uses two complementary clips to show one phone half light, half dark). Slots are drawn in order, so the last is in front |
| A new scene | add a capture stem and scene to `Scene.kt`, teach `StoreScreenshotTour` to reach it, then list it in `scenes.json` |
| The demo vault's contents | `upgrade-test/.../store/DemoVault.kt` (persona Aarav Mehta); the vault is rebuilt from scratch every run |

Headlines fit two lines of about 19 characters at the committed size. Nothing enforces that; look
at the preview page.

## Upload to Play Console

Play Console does not take screenshots from any API this project uses; upload by hand. Main store
listing → Phone screenshots: remove the old set, then add `01-…` to `08-…` in order. The compositor
already enforces the constraints Play checks (2–8 images, 1080×1920, 8-bit RGB, no alpha, under 8 MB).

## Tests

`scripts/tests/store-screenshots-test.sh` runs the compositor against synthetic captures: output
format, README block, stale-output removal, every validation message, the alpha-stripping fallback,
and the committed `scenes.json` + `theme.json` end to end. It needs Chrome and runs in `ci.yml` on
every PR. Run it locally before changing `render.py`, `theme.json` or `scenes.json`.

## Traps

- **A capture shows wifi and battery but no signal icon.** SystemUI's status bar is rebuilt by a
  palette change and keeps believing it is in demo mode, so a plain re-apply restores the clock but
  not the mobile icon. `apply_demo_status_bar` therefore does `demo exit` then `demo enter` first;
  keep that if you touch it. (Seen on every dark and palette capture before the fix, 2026-10-04.)
- **The biometric sheet is black in `screencap`.** It is a `FLAG_SECURE` window. The script takes
  that one capture through the emulator console instead, which reads the emulator's framebuffer;
  that is why it needs an emulator and cannot run against a physical device.
- **The dark unlock screen renders light.** `AnimatedCurveBackground.kt` paints a near-white header
  in dark theme with white status icons on it. An app bug, not a capture bug; until it is fixed the
  unlock and palette images use light captures only.
- **Detail screens print `Date.toString()`** ("Sun Oct 04 11:15:03 GMT+05:30 2026"). Visible in the
  login-detail image; an app presentation issue tracked in `docs/store-listing.md`.
- **One run died straight after `== Install ==`** with only the exit trap's lines (2026-10-04 11:07):
  logcat showed the guest doing a full boot about 15 s after the snapshot restore. Seven other runs
  that day were fine. The script now names the failing command (`ERR` trap) and waits for the
  package manager after boot; if it recurs, add a settle wait after `start_emulator` and record it.
- **`cmd uimode night` reports success for a value it did not apply.** `set_night_mode` reads it
  back and the tour checks again before its first capture; do not drop either check.
- **The AVD must have `hw.keyboard=no`**, otherwise the search capture has no soft keyboard. The
  script refuses to run against an AVD with a hardware keyboard rather than silently producing a
  different image.
- **Chrome on macOS prints `task_policy_set` errors** when run headless. Harmless.
- **`frames/pixel_8/mask.webp` is not an alpha mask.** It is Android Studio's foreground overlay:
  opaque black at the rounded corners and the camera cut-out, transparent elsewhere, so it is drawn
  *on top of* the capture. Using it as a CSS mask hides everything except the corners.
