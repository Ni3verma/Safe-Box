# 6. Store screenshots are composited from emulator captures of the real app

Date: 2026-10-04

Status: Accepted

## Context

The Play Store listing images had been made by hand once and never updated; by `v2.2.5.0` they
showed neither the TOTP feature nor the current UI, and the README reused the same stale PNGs. The
listing needed to become something that is regenerated after a UI change the way a test is re-run,
with the copy and the look under version control.

Three things made this harder than "take screenshots and add a headline":

- The app has to be driven into each scene on a **fresh install**: sign-up with a master password
  that passes `PasswordValidator`, a seeded vault including authenticator records (reached only
  through the QR scanner, whose manual-entry button needs the camera permission already granted),
  the backup folder granted through `OpenDocumentTree`, and a fingerprint enrolled on the device
  for the biometric sheet.
- The biometric sheet is a **`FLAG_SECURE`** window: `screencap`, UI Automator and every other
  SurfaceFlinger-based screenshot returns black while it is up.
- The images must be identical from run to run apart from the app's own pixels: same clock,
  status icons, font scale, Material You palette and appearance.

## Decision

**Capture with the project's own UI Automator harness on a dedicated AVD, then composite with a
stdlib-Python script driving headless Chrome.**

- `scripts/take-store-screenshots.sh` boots `SafeBox_Store_Pixel_8_API_35` (created and provisioned
  by `scripts/lib/store-avd.sh`: no hardware keyboard, PIN, enrolled fingerprint), pins the device
  (SystemUI demo mode, font scale, Material You seed), and runs `store.StoreScreenshotTour` in
  `:upgrade-test` once to build the demo vault and once per appearance to photograph the scenes.
  The biometric sheet alone is taken through the **emulator console**, which reads the emulator's
  framebuffer and so is not blinded by `FLAG_SECURE`.
- `scripts/store-screenshots/render.py` lays each raw capture into the Pixel 8 device art from
  Android Studio, adds background and copy from `theme.json` and `scenes.json` through an HTML
  template, rasterises with Chrome's `--screenshot`, and verifies the result is what Play accepts.
  It also rewrites the README grid, so the README cannot drift from the listing.
- The qa build is used, with its label overridden to the production one (`-PappLabel="Safe Box"`),
  because the biometric sheet prints the label and the release APK is not built locally.

Evidence that it holds: eight images rendered from a clean run on 2026-10-04 in about 5 minutes
including boot; `scripts/tests/store-screenshots-test.sh` (19 cases) runs on every PR.

## Alternatives rejected

| Option | Why not |
|---|---|
| [goldie](https://github.com/software-mansion-labs/goldie) + argent flows | Needs Node 20 and ffmpeg, neither in this project's toolchain or CI image, and its flows would have to re-implement what the `:upgrade-test` harness already does (sign-up, scanner-then-manual authenticator entry, SAF folder grant, fingerprint enrolment). Its screenshot path cannot see the `FLAG_SECURE` sheet, and its Play bezel is a Pixel 10 Pro while the harness runs a Pixel 8 profile. |
| Fastlane `screengrab` + `frameit` | Ruby toolchain for one task; `frameit` composes a single phone per image, whereas the listing wanted two-phone and split layouts; same `FLAG_SECURE` limit. |
| Compose preview screenshot testing | Renders composables, not the app: no real data pipeline, no system UI, no biometric prompt, and the listing should show what users get. |
| Pillow / ImageMagick compositing | Neither is installed; text layout with a variable font, drop shadows and rotations are exactly what a browser does well and a pixel library does badly. Chrome is already present wherever a developer works. |
| A `:ui-harness` module separate from `:upgrade-test` | Rejected by the owner: the tour reuses the harness's selectors, record editor and settings driver, and a second self-instrumenting module would duplicate them. |
| One shared AVD with the developer's | A screenshot run must set a PIN, enrol a fingerprint and remove the hardware keyboard; doing that to a development AVD changes how it behaves for everything else. The dedicated AVD costs one extra cold boot. |

## Consequences

- Raw captures are gitignored; the committed artefacts are the 1080×1920 store images, the two JSON
  files and the font. A fresh clone needs one full run before `--render-only` works.
- The compositor is standard-library Python plus Chrome. Changing to another renderer means
  changing one function (`rasterise`) and keeping `verify_store_image`.
- Two app presentation issues became visible in the images and are tracked in
  [docs/store-listing.md](../store-listing.md): raw `Date.toString()` on detail screens and the
  dark-theme unlock header. Fixing them is app work, not pipeline work.
