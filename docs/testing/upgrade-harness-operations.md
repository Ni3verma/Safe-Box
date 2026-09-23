# Upgrade harness — operations

How to run, extend and debug the APK-over-APK harness. The *why* lives in
[upgrade-testing.md](upgrade-testing.md); this file is the part you need when writing the next MR.

---

## What exists

| Piece | Path | Responsibility |
|---|---|---|
| Gradle module | `upgrade-test/` | builds the instrumentation APK. **Assembled only, never run, by Gradle** |
| On-device driver | `upgrade-test/src/main/java/.../UpgradeSmokeTest.kt` | UI Automator, one `@Test` per phase |
| Fixtures | `upgrade-test/src/main/assets/fixtures/` | golden `.bak` files, packaged into the harness APK |
| Host orchestrator | `scripts/run-upgrade-test.sh` | install, seed, upgrade, re-run, collect |
| Determinism check | `scripts/check-oracle-determinism.sh` | runs the orchestrator N times and diffs the oracle |
| Zero-test guard | `scripts/lib/instrumentation-guard.sh` | decides whether a run actually happened |
| Guard's own tests | `scripts/tests/instrumentation-guard-test.sh` | device-free, run by `ci.yml` on every PR |
| Baseline resolution | `scripts/resolve-baselines.sh` | derives tags from the release list |
| Baseline download | `scripts/fetch-baseline-apk.sh` | pulls `SafeBox-qa.apk` off a GitHub Release |
| CI job | `.github/workflows/upgrade-test.yml` | manual only until MR6: dispatch, or the `run-upgrade-test` PR label |

The module is a self-instrumenting `com.android.test` module with **no compile dependency on
`:app`** — `:upgrade-test:assembleDebug` runs zero `:app` tasks. Do not add one; that coupling is
exactly what ADR-0001 says breaks against minified builds.

## Running it

```bash
./scripts/fetch-baseline-apk.sh v2.0.4.0 old-apk/          # needs gh, network
./gradlew assembleQa :upgrade-test:assembleDebug
ANDROID_SERIAL=emulator-5554 \
  ./scripts/run-upgrade-test.sh old-apk/SafeBox-qa.apk app/build/outputs/apk/qa/SafeBox-qa.apk
```

Output lands in `upgrade-test-out/` (override with a third argument): one
`instrumentation-<phase>.txt` per phase, plus `logcat.txt`, collected even on failure.

> [!CAUTION]
> The run **uninstalls `com.andryoga.safebox.qa`** and then signs up from scratch, so it must not
> be pointed at a handset. Two protections exist. The target is resolved once and exported as
> `ANDROID_SERIAL`, so a device appearing mid-run cannot redirect a later step; and a target whose
> `ro.build.characteristics` does not contain `emulator` is refused outright unless
> `UPGRADE_TEST_ALLOW_PHYSICAL=1` is set. With a phone attached alongside the emulator, an
> unpinned run stops with `expected exactly one connected device, found 2` and lists them.
> Developer handsets routinely carry the qa, debug *and* release variants at once — they have
> distinct applicationIds, so only the qa vault is at stake, but it is still someone's data.

In CI there are two ways in, and neither runs on its own:

- dispatch **Upgrade Test (manual)** with a rule (`previous`, `schema-boundary`, `oldest`) or an
  explicit `vMAJOR.MINOR.DB.FIX` tag — anything else is rejected;
- add the **`run-upgrade-test`** label to a pull request, which runs it from that PR's head branch
  against the `previous` baseline. Removing and re-adding the label runs it again.

> [!IMPORTANT]
> The label path exists because `workflow_dispatch` is unusable on a branch whose workflow file has
> not reached the default branch yet: GitHub only shows the "Run workflow" button, and only accepts
> an API dispatch, for workflows present on `master`. Before this file merges, the workflow is
> invisible to `gh workflow list` and cannot be dispatched at all — which is exactly when the first
> evidence that the job works is most needed.

> [!NOTE]
> The CI emulator is **API 34**; the emulator available locally is a Pixel 8 on **API 35**. The
> difference is deliberate (decision 6) — a local pass does not imply a CI pass.

## Adding a phase

A phase is one `@Test` method plus one line in the orchestrator. To add one:

1. Write the method in `UpgradeSmokeTest` (or a new class alongside it).
2. Add `run_phase <label> <methodName> <minimum-tests>` to `run-upgrade-test.sh`, in the position
   relative to `adb install -r` that the phase needs.
3. Leave the minimum-test count at `1`, and add a separate `run_phase` call for each further
   method. One call selects exactly one method, so any higher minimum can only ever fail.

Phases are invoked one method at a time by `-e class <fqcn>#<method>`. That is not stylistic:

- a comma-separated filter silently runs only the first class and reports success;
- a filter matching nothing exits 0 printing `OK (0 tests)`.

`assert_instrumentation_ran` is what makes both fatal. **Never add a phase that bypasses it.** A
`min_tests` of 0, or anything that is not a positive integer, is rejected by the guard itself — it
used to be accepted, and `0` made the guard pass the very run it exists to catch.

## Writing selectors

Production code contains **no `Modifier.testTag`** and the APK under test is minified, so
everything is found by visible text or content description.

| Trap | Detail |
|---|---|
| Mandatory field labels carry an asterisk | `MandatoryLabelText` appends `*` **inside the same text node**, so the signup password label is `Password*`, not `Password`. The unlock screen uses a plain `Text`, so there it really is `Password`. |
| Label and input are different nodes | Find the label, then search its parent for `By.clazz("android.widget.EditText")`. |
| Masked fields render as bullets | Tap `content-desc="Toggle sensitive data visibility"` first — it exists in the shipped APK. |
| `By.text(String)` is an exact match | It quotes the argument, so `Password*` is safe to pass literally. |
| Copy changes break tests | The cost of black-box selectors. Keep every string in the `companion object`, never inline. |

Every lookup must fail with the window hierarchy attached. `describeScreen()` does this; use it in
all new failure messages. A CI failure reading `NullPointerException at line 47`, on an emulator
nobody can attach to, is worthless.

## Launching the app

The driver resolves a launch intent through `PackageManager`, which needs the `<queries>` entry in
`upgrade-test/src/main/AndroidManifest.xml`. If the app's `applicationId` ever changes, that entry
must change with it or the launch fails with a null intent.

> [!IMPORTANT]
> **Never wait on `By.pkg(APP_PACKAGE).depth(0)` to decide the app is up.** On a device with an
> enrolled fingerprint, the unlock screen raises a system biometric sheet the instant it appears.
> That sheet belongs to **SystemUI**, so the app stops being the foreground package and the wait
> times out after 30 s while the app is running perfectly — reported as
> `did not reach the foreground`, with no crash in logcat. Observed on a Pixel 8 API 35 emulator,
> 2026-09-22.
>
> `launchAppUnderTest(expectedHeading)` waits on the expected screen's text instead and, if that
> times out, presses back to dismiss the prompt. The app treats that as `onErrorOrCancel` and falls
> back to the password field, which is what a user who wants to type their password does.
>
> The back press is **not** sufficient on its own. On a gesture-navigation device it was also seen
> to send the task to the launcher (`RecentsController.finishInner: toHome=true` in logcat), after
> which the test asserted against the home screen and failed with `unlock screen has no 'Password'
> field` — with a launcher hierarchy attached. So each of the three attempts re-issues the launch
> intent; bringing a backgrounded task forward is harmless, and the prompt is not re-armed because
> the app offers biometric unlock only once per process.
>
> CI's emulator has no biometric enrolled, so both of these are flakes that reproduce
> **only locally**. Do not "fix" them by deleting the recovery path because CI is green.

## Seeding the vault

Phase A signs up and then restores `v2_pre_totp.bak` through the real document picker and the real
restore worker. Three things about that path were established by driving it, not by reading code,
and each one costs an afternoon to rediscover.

**The fixture is pushed by the host, not by the test.** Instrumentation can only write to
app-private storage, which the picker cannot see. `scripts/run-upgrade-test.sh` pushes it to
`/sdcard/Download` and then asks MediaProvider to rescan, because DocumentsUI lists that root from
the media database rather than from the filesystem — a pushed file nobody announced is on disk but
not offered. The file *name* is passed on to the instrumentation with `-e fixtureFile`, so the host
is its single owner.

**The picker does not open where the file is.** It opens on *Recent*, which on a fresh emulator
shows `No items`, so waiting for the filename on the first screen always times out.
[SafDocumentPicker](../../upgrade-test/src/main/java/com/andryoga/safebox/upgradetest/SafDocumentPicker.kt)
opens the roots drawer and chooses `Downloads` explicitly. Do not select the root below it — that
is the device's own name (`sdk_gphone64_arm64` locally, something else everywhere else).

> [!IMPORTANT]
> **"Data has been successfully restored." is not a completion signal.** The dialog appears while
> the records list is still catching up: asserting immediately after it reproducibly found five of
> the seven restored records, and the two missing ones were simply not in the view hierarchy yet.
> Anything reading restored data must wait for it — `scrollToText`'s second, patient pass is where
> that waiting lives. Note also that a list short enough to fit on screen exposes **no**
> `scrollable` node at all, so "scroll to find it" silently does nothing, which is why a missing
> scroll container is treated as normal rather than as an error.

> [!WARNING]
> **DocumentsUI has two package names and the harness must match both.** The local emulator has
> Google APIs and ships `com.google.android.documentsui`; a plain AOSP image, which is what CI
> runs, ships `com.android.documentsui`. Every selector here — the package itself and the
> breadcrumb resource ids — is a `Pattern` accepting either. Pinning one name makes the picker
> invisible on the other half of the fleet, and the symptom is not obviously a package problem:
> `awaitPicker` simply burns its 20 s and reports that the picker never opened, on a device where
> it opened perfectly.

> [!CAUTION]
> **Do not run this harness on an ATD image.** ATD ("Automated Test Device") images strip the
> system apps a test is assumed not to need, and DocumentsUI is one of them. `ACTION_OPEN_DOCUMENT`
> then resolves to `com.android.fakesystemapp`, a placeholder with an empty action bar, and the
> failure reads identically to the package-name problem above — which is how it costs an hour.
> Tell them apart from the hierarchy in the failure message: a package of
> `com.android.fakesystemapp`, on a `320x640` screen, means the image, not the selector.
> Measured on `aosp_atd` in run
> [35863723921](https://github.com/Ni3verma/Safe-Box/actions/runs/35863723921). `google_atd` is
> **not** tested here; the app reduction is a property of ATD rather than of the AOSP variant, so
> treat it as equally unusable until someone shows otherwise.

> [!CAUTION]
> **Setting a field's text is not the same as the app having the text.** `ACTION_SET_TEXT` returns
> once the node accepts it; the value reaches the ViewModel one Compose `onValueChange` later, and
> the forms save `_uiState.value` at the instant Save is pressed. Type-then-immediately-save
> therefore races that hop, and losing it writes an **empty record** instead of failing — the form
> closes exactly as it would on success. The symptom surfaces far away: a later lookup cannot find
> the row, because the record that was written has a blank title and sorts to the top of the list,
> off the screen the failure dumps. `UiSupport.typeInto` blocks until the field reads back
> non-empty for this reason; do not "simplify" it back to a bare `text = value`. Evidence: run
> [35875270225](https://github.com/Ni3verma/Safe-Box/actions/runs/35875270225), where the Note
> form's fields were set 168 ms before Save on a CI emulator, after ten consecutive local passes.

After the restore, Phase A creates one record of each type through the app's own forms
([RecordCreator](../../upgrade-test/src/main/java/com/andryoga/safebox/upgradetest/RecordCreator.kt)),
because a restore and a user's own input are two different encryption call sites and a defect in
either is invisible if only the other is exercised.

**That order is forced, not chosen.** Restoring *replaces* the vault — the baseline's own wording is
"Restoring replaces everything currently saved in the app… Any record that is not in the backup file
will be permanently lost." Creating the records first and restoring afterwards would silently delete
them, and Phase A would then assert against records the restore had just destroyed.

### Granting a backup directory

Phase A then points the app at a backup directory and turns two settings off, so that the upgrade
has state to lose that is not just records:
[SettingsChanger](../../upgrade-test/src/main/java/com/andryoga/safebox/upgradetest/SettingsChanger.kt)
flips `Privacy mode` and `Auto-backup on login`, both of which ship **on**. A preference left at its
default survives even a migration that drops the store and rebuilds it, so asserting on defaults
proves nothing.

The backup location goes through `ACTION_OPEN_DOCUMENT_TREE`, which behaves nothing like the file
picker above:

| | File picker | Tree picker |
|---|---|---|
| Opens on | *Recent* (`No items` on a fresh emulator) | the storage root, or wherever it was last left |
| Roots drawer | `Show roots` button in the toolbar | none |
| Confirming | tapping the file | `USE THIS FOLDER`, then `ALLOW` in a system dialog |

> [!WARNING]
> **Android refuses to grant a tree over the root of shared storage, or over `Download`.** The
> picker shows "Can't use this folder / To protect your privacy, choose another folder" and
> `USE THIS FOLDER` does nothing — which from the outside is indistinguishable from a tap that
> missed. Phase A therefore grants a dedicated directory, `/sdcard/SafeBoxUpgradeTest`, created by
> the host and passed in with `-e backupDir`.

Two smaller facts, both load-bearing. The host removes and recreates that directory every run, so a
backup file written by an earlier run cannot change what Phase A produces. And unlike the Downloads
root, it needs **no** MediaProvider rescan to become visible — the tree picker lists directories
from the filesystem, so a host-created directory is offered immediately.

The settings screen is a flat column: each switch is a sibling of its label at the same depth, with
no row container, no text and no content description. Nothing but geometry connects the two, which
is what `UiSupport.switchBeside` exploits — the switch whose vertical extent overlaps the label's.
Sliders on the same screen are deliberately left alone: a drag cannot be made byte-deterministic
across devices, and Phase A has to reproduce itself exactly.

### The accessibility cache goes stale, and it costs a day

> [!CAUTION]
> **Never use `UiDevice.wait`/`Until` in this harness.** Those APIs match against the accessibility
> node tree cached in the *test* process, and that cache is not reliably invalidated when the app
> navigates. Every lookup must go through `UiSupport.findOrNull`/`awaitObject`/`awaitGone`, which
> empty the cache before each poll.

What this looks like when it bites, all observed on 2026-09-23 driving `v2.0.4.0` on the Pixel 8
API 35 emulator:

- After dismissing the restore dialog and tapping the `Records` tab, the tree showed the records
  list **underneath the Backup & Restore screen's app bar**, with that tab still marked selected.
- After saving a record, the tree showed the records list **underneath the add-record form's app
  bar** — `Back button`, title `Login`, `Save` — so the add button did not exist as far as the test
  was concerned.
- The stale state survived a **60 s** wait, then read correctly within seconds of the
  instrumentation process exiting. The app was never wrong; only this process's copy was.

The flush is `UiDevice.setCompressedLayoutHeirarchy(false)`, used for its side effect: it calls
`UiAutomation.setServiceInfo`, which empties the cache. Passing the UiAutomator default means the
hierarchy itself is unchanged. Flushing per poll rather than per wait is worth ~15 s of wall clock
per Phase A run, because a lookup that flushes only once can still cache a pre-navigation snapshot
and then sit out its whole timeout against it.

The same cache has a second, opposite failure mode: a handle obtained from one lookup can be
**invalid by the time it is used**, because finding a node and acting on it are separate round
trips and anything that re-lays out in between — a drawer sliding open, a form animating in —
invalidates it. UI Automator raises `StaleObjectException` from `click()`, which reads as a harness
crash rather than as the timing blip it is; it aborted a run on 2026-09-23 tapping `Downloads` one
tap after opening the picker's roots drawer. So no call site holds a handle across two operations:
tapping goes through `UiSupport.clickText`/`clickObject` and typing through `UiSupport.typeInto`,
which re-find from the selector and retry. Where a handle genuinely must be reused — reading a
switch's state and then toggling it — wrap the pair in `UiSupport.retryingOnStale`.

> [!TIP]
> **Waiting and scrolling are different problems, and patience belongs to the second pass.**
> `scrollToText` glances for 2 s, walks the list, and only then rewinds and repeats the walk with
> the full 15 s patience. Spending the patience first charges the whole timeout to every lookup
> whose only crime is sorting below the fold: measured from UiAutomator's own poll logging, twelve
> such lookups cost **181 s of a 269 s Phase A**, each for a record that was on screen one swipe
> later. The same measurement is the cheapest way to find the next one —
> `scratch/poll_timeline.py` style grouping of `Retrieving node with selector` lines shows exactly
> which selector burned the clock.

Two obvious ways to check what the screen "really" shows are unavailable here, so do not waste time
on them:

| Tool | Why it fails |
|---|---|
| `adb exec-out screencap` | The app sets `FLAG_SECURE`, so every screenshot is solid black. |
| `adb shell uiautomator dump` | Refuses while instrumentation holds the accessibility connection; it only works before or after a run, which is exactly when the bug is not visible. |

`adb shell dumpsys window windows` does work and is how "nothing is covering the app" was
established.

## Capturing the oracle

Phase A ends by reading the whole vault back through the UI and writing
`phase-a-oracle.txt` into the *harness's* app-private storage. The host pulls it with
`adb exec-out run-as com.andryoga.safebox.upgradetest cat files/phase-a-oracle.txt` — which works
because the harness APK is debuggable — and fails the run if it comes back empty. It is written to
a file rather than printed because the acceptance is a byte comparison and logcat adds timestamps
and truncates long lines.

What goes in it is constrained by one rule: **nothing time-varying**. `Created on`, `Updated on`
and the backup screen's "last taken on" all move every run, so the oracle reads *named fields
only* and never dumps a screen. A filter would have worked until the first value somebody forgot,
at which point the ten-run acceptance becomes noise that everyone learns to ignore.

Check it with:

```bash
ANDROID_SERIAL=emulator-5554 \
  ./scripts/check-oracle-determinism.sh old-apk/SafeBox-qa.apk \
  app/build/outputs/apk/qa/SafeBox-qa.apk 10
```

Ten full runs at roughly two minutes each, stopping at the first run whose oracle differs from
run-01 and printing the diff.

### Reading the records list

There is no node per row. A row's title, its subtitle and its type chip are three unrelated text
nodes that happen to share a line, so rows are reconstructed from geometry: a chip is right of
centre, its title is the topmost text left of centre on the same line, and the subtitle is ignored
because it sits lower. Detail screens work the same way — `UiSupport.valueBelow` takes the nearest
text below a label whose horizontal extent overlaps it, which is load-bearing on the bank account
screen where `MICR Code` and `IFSC Code` share a line.

Geometry cannot tell a wrong answer from a right one, so the result is checked against the titles
Phase A seeded, in both directions, and both directions have fired for real:

| Trap | What it looked like | What it actually was |
|---|---|---|
| The list's **type-filter row** | A twelfth record, `Login`, of type `Note` | `Login`, `Card`, `Bank Account` and `Note` are also filter chips on one line at the top of the list, and `Note` sits right of centre, so it was read as a row's chip and paired with the leftmost thing on its line. A record row names exactly one type, so a type name sharing a line with another type name is a filter and is dropped. |
| Reading **only while the list can still scroll** | A perfectly reproducible oracle missing the last four records | The final screenful arrives *after* the scroll that reports there is nothing left. Read after every scroll, then break. |

> [!IMPORTANT]
> The records screen's app bar **collapses**: while the list is scrolled down, `Add new record
> button` does not exist in the hierarchy at all, and waiting for it burns a full 15 s timeout
> against a screen that is behaving correctly. Call `UiSupport.scrollToTop()` before looking for
> anything in that app bar. `scrollToText` only travels downwards, so rewinding is also what makes
> one lookup independent of where the previous one finished.

## What the orchestrator already guarantees

Do not re-implement these in a test:

- both APKs declare `com.andryoga.safebox.qa` — a debug or release APK is rejected before any
  install, because three build types mean three applicationIds and none can upgrade into another;
- the build under test has a strictly higher `versionCode` than the baseline;
- the upgrade is `adb install -r` with **no `-d`**, after `am force-stop` — never `pm clear`, never
  `uninstall`;
- `firstInstallTime` is non-empty before the upgrade and unchanged after it, and `versionCode`
  changed — together, proof that `/data` survived rather than the app being reinstalled;
- every phase executed at least its minimum number of tests, and none failed.

## Triage

App-level symptoms are in [upgrade-testing.md section 9](upgrade-testing.md#9-failure-triage). These
are harness-level ones:

| Symptom | Cause |
|---|---|
| `0 test(s) executed, expected at least 1` | the `-e class` filter matches nothing — usually a renamed or misspelled method |
| `instrumentation component could not be started` | the harness APK is not installed, or the runner name drifted from the manifest |
| `no launch intent for com.andryoga.safebox.qa` | app not installed, or the `<queries>` entry no longer matches its applicationId |
| `declares applicationId '...debug'` | a debug APK was passed; only `qa → qa` can upgrade |
| `the build under test (N) does not supersede the baseline` | on CI, `GITHUB_RUN_NUMBER` counts runs of that one workflow, so a new workflow builds a `versionCode` below released ones — the job pins it, see `upgrade-test.yml` |
| `could not read firstInstallTime` | the install did not take, or `dumpsys package` output changed shape |
| `no aapt2 found under .../build-tools` | `ANDROID_HOME` points at an SDK with no build-tools |
| `<tag> has no SafeBox-qa.apk asset` | releases before the upload step existed — `v1.0.0`, `v1.1.0`, `v1.2.2.0` — carry none |
| Test times out in `launchAppUnderTest` after four or five runs | the emulator `/data` partition is full; see the build-and-test skill |
| An assertion fails and the attached hierarchy is `nexuslauncher`, not the app | the app was sent home rather than merely backgrounded — see [Launching the app](#launching-the-app) |

## Baseline resolution

`resolve-baselines.sh` reads the release list and applies three rules, filtering to stable releases
that actually carry a `SafeBox-qa.apk`. As of 2026-09-22 it emits:

```json
{"from_tag":["v2.0.4.0","v1.3.3.1","v1.3.3.0"]}
```

`--rule previous|schema-boundary|oldest` prints a single tag instead, which is what the manual
workflow uses. The current Room schema is read from `app/schemas/`, not from a tag, because the tag
for the build under test does not exist yet when the script runs.

An optional floor lives in `upgrade-test/oldest-supported.txt`, one tag on a line. It does not
exist by default and should stay that way until "how far back do we support" is an actual product
decision.
