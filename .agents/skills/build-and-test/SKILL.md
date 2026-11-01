---
name: build-and-test
description: Exact commands, environment setup and known traps for building Safe-Box and running its unit, instrumentation and managed-device tests
---

# Building and testing Safe-Box

## Environment

- Gradle needs a JDK that a bare macOS shell lacks:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>`.
- Gradle, `adb`, the emulator **and `git commit`** (the pre-commit hook shells out to Gradle) run
  **outside the sandbox**; inside it Gradle fails with `Could not connect to the Gradle daemon`.
  `git commit` also needs the `JAVA_HOME` prefix, or it fails with `Unable to locate a Java Runtime`.
- `git push` is not possible from the agent; commit locally (github-tooling skill).
- SDK tools (`aapt2`, `apksigner`, `dexdump`): `~/Library/Android/sdk/build-tools/<version>/`.

## Commands

| Goal | Command |
|---|---|
| All unit tests | `:app:testDebugUnitTest` |
| Instrumentation APK only | `:app:assembleDebugAndroidTest` |
| All instrumentation tests on a connected device | `:app:connectedDebugAndroidTest` |
| One instrumentation class (**one only**, see Traps) | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` |
| One package in one process on the CI image (reproduces CI ordering) | `:app:pixel8Api34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=<package>` |
| Managed device, everything (no device needed, matches CI) | `:app:pixel8Api34DebugAndroidTest` |
| Coverage (opt-in, slow) | add `-Pcoverage` |
| Lint as CI runs it | `:app:lintRelease` |
| Minified QA APK | `:app:assembleQa` |
| Upgrade/restore harness, build only | `:upgrade-test:assembleDebug` |
| Restore / upgrade test | `./scripts/run-restore-test.sh <qa.apk>` / `./scripts/run-upgrade-test.sh <N-1 qa.apk> <N-1 seed.bak> <qa.apk>` |
| Every script test (no device) | `rc=0; for t in scripts/tests/*.sh; do bash "$t" \|\| rc=1; done; [ "$rc" = 0 ]` |

Harness procedures, including fetching N-1: [upgrade-harness-operations.md](../../../docs/testing/upgrade-harness-operations.md).
A `.bak` for a seed or a repro comes from a `debug` build: the format does not depend on the build
type and `assembleQa` is much slower (user instruction, 2026-09-26).

### Documentation checks

| Check | Where | Command |
|---|---|---|
| Broken relative links | CI (lychee) | `lychee --offline '.agents/**/*.md' 'docs/**/*.md' 'upgrade-test/**/*.md'` |
| Stray agent markup, Windows link targets | CI, and the pre-commit hook on staged files | `git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'` |

`DOCS_POLICY_PATTERN` lives in `CICD/gitHooks/pre-commit.sh`; extract it, never paste it into a
markdown file (the file would then match itself):

```bash
DOCS_POLICY_PATTERN=$(sed -n "s/^DOCS_POLICY_PATTERN='\(.*\)'$/\1/p" CICD/gitHooks/pre-commit.sh)
git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'   # exit 1 with no output = clean
```

lychee is not installed and there is no `brew`/`cargo`/`npm`; fetch the binary if needed:
`curl -sSL -o lychee.tar.gz https://github.com/lycheeverse/lychee/releases/download/lychee-v0.24.2/lychee-aarch64-apple-darwin.tar.gz && tar xzf lychee.tar.gz`.
It skips Windows drive-qualified targets (`C:\…`) as an unknown URI scheme, which is why the grep exists.

## Git hooks

Nothing installs the hook (`CICD/cicd.gradle` is orphaned, so there is no detekt task either):

```bash
cp CICD/gitHooks/pre-commit.sh .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
diff .git/hooks/pre-commit CICD/gitHooks/pre-commit.sh   # the copy drifts silently; check before trusting it
```

Every commit prints `Detekt task is not available in this build` and continues. The hook runs
`git add -u` (so `git hook run pre-commit` stages unstaged work) and runs
`scripts/tests/backup-format-test.sh` when a backup-format file is staged.

## Traps

### `BUILD SUCCESSFUL` does not mean your tests ran

A class filter that matches nothing — or, with a comma-separated list, only some classes — still
exits 0. **One class per invocation**, results directory deleted first, XML parsed afterwards;
never trust the exit code:

```bash
rm -rf app/build/outputs/androidTest-results
# ... one connectedDebugAndroidTest invocation ...
python3 -c "
import glob, xml.etree.ElementTree as ET
fs = glob.glob('app/build/outputs/androidTest-results/**/*.xml', recursive=True) or exit('NO RESULTS FILE')
for f in fs:
    for ts in ET.parse(f).getroot().iter('testsuite'): print(ts.get('name'), ts.get('tests'), ts.get('failures'))
"
```

The file is `TEST-<device>.xml` with spaces and parentheses in the name; the root is `<testsuites>`.
`am instrument` has the same hazard (`OK (0 tests)`, `INSTRUMENTATION_CODE: -1` = success); pipe
its output through `./scripts/lib/instrumentation-guard.sh out.txt 1`, and use
`scripts/lib/crash-sentinel.sh` for a crash of the app under test. Run the script suites with the
loop in [Commands](#commands): a bare `for` exits with the last suite's status.

### The emulator runs out of disk and it looks like an app bug

A full run uses ~1.2 GB of `/data`; after about four, installs fail with `Requested internal only,
but not enough space`, the app never renders, and every test times out in `unlockApp` after 25 s.
Check `adb shell df -h /data` before blaming the app; recreate with
`emulator -avd <name> -wipe-data -partition-size 8192`.

### A manually granted camera permission fails the scanner tests

The `-r` install keeps a runtime grant, so `QrScannerScreen` skips its rationale dialog and the
`AddNewRecordFlowsE2ETest` scanner cases time out. Before the first run after manual testing:
`adb shell pm revoke com.andryoga.safebox.debug android.permission.CAMERA`.

### A component test that copies to the clipboard fails when run on its own

`rememberCopyToClipboardAction` reaches `WorkManager.getInstance`, which needs a Hilt component a
plain `createComposeRule()` test never creates (`The component was not created`). It passes in a
full run only because an earlier Hilt test initialised WorkManager. Such tests stand WorkManager up
in `@Before` with `WorkManagerTestInitHelper` and a `SynchronousExecutor` (`TotpBadgeTest`).

### WorkManager keeps the first test's Hilt component

WorkManager is initialised once per process and keeps its `WorkerFactory`; `HiltAndroidRule` builds
a component per test, so a factory captured once gives every later worker the first test's
singletons — harmless with the old process-wide Keystore key, fatal once the key became session
state (MR 3). `BaseTestApplication` resolves `HiltWorkerFactory` per `createWorker`. The check that
proves it: the `e2e` package in one invocation on the managed device.

### Back on the Login root finishes the Activity

A back press on Login (`onBackPressedDispatcher.onBackPressed()`) finishes `MainActivity`; every
Compose assertion after it fails with `No compose hierarchies found` except `assertDoesNotExist`,
which is exactly the back-stack check (a back press that revealed Home instead would make the
node exist). Make the press the last step, followed only by that assertion.

### Racing a tap against a session lock needs a raw `dispatchTouchEvent`

`performClick()` synchronises with Compose first, so a lock issued just before it has already run
the HomeGraph gate and the tap lands on Login. Read `fetchSemanticsNode().boundsInWindow.center`,
then in one `scenario.onActivity { }` lock and `activity.dispatchTouchEvent(DOWN/UP)` at that point.
Keep the control test that saves with the same raw tap and no lock; without it a missed tap passes
silently (`HomeScreenNavigationAndTimeoutE2ETest`).

### On GMD `pixel8Api34`, a `waitForIdle` phase can silently cost ~7 s

In the `e2e` package run the app goes quiet for 5–8 s once or twice per test, in every class, while
the test thread waits for idle (28–48 silences per 60-test run; each ends 2.0 s after Firebase
Analytics logs `AppMeasurementService is shutting down`; cause not identified, 2026-10-23). A
journey chaining several waits can therefore exceed a 25 s `waitUntil` on a cold run while passing
alone. Before calling such a failure a regression, keep the per-test
`logcat-<class>-<method>.txt` from `app/build/outputs/androidTest-results/managedDevice/debug/pixel8Api34/`
(the next invocation overwrites it) and look for the silences.

### Two adb binaries kill each other's server mid-run

`/usr/local/bin/adb` (on `PATH`) and `$ANDROID_HOME/platform-tools/adb` differ in version; whichever
connects next kills the other's server, and an `am instrument` running inside it dies with it.
Before a long run with Studio open, compare `adb version` with the SDK copy and, if they differ,
prefix `PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"`. The stale copy is root-owned.

### Image and device limits

- Instrumentation runs on `debug` only; `qa` gives a silent `tests=0`
  ([ADR-0001](../../../docs/decisions/0001-instrumentation-tests-run-on-debug-only.md)).
- The GMD `aosp-atd` image has no secure lock screen: `locksettings set-pin` is ignored and
  auth-bound Keystore keys cannot be generated. Anything needing a PIN or a biometric uses a
  `google_apis` image. Never probe for a shell binary from a test through
  `UiAutomation.executeShellCommand`; a missing one killed the instrumentation
  ([ADR-0011](../../../docs/decisions/0011-envelope-encryption-spike-results.md) S8).
- Fingerprints on `google_apis` emulators: enrol in Settings, then
  `adb -s emulator-NNNN emu finger touch <id>`; the console is reachable from the guest at
  `10.0.2.2:<console port>` after `auth <token>` (`~/.emulator_console_auth_token`).
  Scripted: `adb shell am start -a android.settings.FINGERPRINT_ENROLL`, type the PIN, tap
  *Next*, then ~8 `emu finger touch` 1.5 s apart (used for the MR 6 manual pass, 2026-10-31).
- `adb shell locksettings clear --old <pin>` on an emulator removes the credential but **leaves the
  fingerprint templates enrolled** (a real device deletes them), so `BiometricManager` still
  reports a strong biometric and `VaultKeyMaintenance` attempts a biometric wrap that fails with
  `User authentication required` (`VAULT_WRAP_CREATE_FAILURE`). Harmless, emulator-only
  (API 30 `google_apis`, 2026-10-31).
- API 29 `google_apis` image (r13), AVD `API_29`: `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`
  returns `-1` (`STATUS_UNKNOWN`) with a fingerprint enrolled, so no biometric wrap is created and
  biometric login is never offered — fingerprint *recovery* through the credential prompt works.
  Tapping **Use PIN** on that prompt makes `BiometricService` log `Cancel received while
  ConfirmDeviceCredential showing` and deliver `ERROR_CANCELED (5)` to the app before the PIN
  screen is used; androidx 1.1.0 issues no cancel on API 29, so this is the platform/image
  (2026-10-31, see design §9.3 MR 6). No physical API 29 exists in Test Lab or Device Streaming
  either, so this is the shipped Android 10 behaviour; the design's §8.2 GA trigger decides what
  happens to the API 29 recovery path.

### Build and lint quirks

- `app/build.gradle` wires `compileDebugAndroidTest*` / `dexBuilderDebugAndroidTest*` to depend on
  `kspDebugAndroidTestKotlin`; a new variant or renamed task needs that block updated or Hilt's
  generated test sources go missing.
- Lint's `NewApi` resolves a guard only when it reads `Build.VERSION.SDK_INT` (or a
  `@ChecksSdkIntAtLeast` method); an injected `sdkInt` parameter fails `lintRelease`.
- `unitTests.returnDefaultValues = true`: unmocked framework calls return defaults, so a unit test
  can pass while doing nothing. Assert on observable state.
- Every androidTest runs over Play's `FakeAppUpdateManager`
  ([driving it](../../../docs/architecture/in-app-updates.md#driving-the-fake-in-instrumentation-tests)).

## Selector strategy for UI tests

Find everything by text or content description. `Modifier.testTag`s (`ui/core/TestTags.kt`) exist
only where a label cannot identify the element (switches and sliders, buttons whose label equals a
heading, the records list and rows); in `debug`/`qa` they are also UI Automator resource ids, so
black-box tests can drive even the minified QA APK (`adb shell uiautomator dump /sdcard/ui.xml`).

## Inspecting a built APK

```bash
aapt2 dump badging app.apk | head -1                  # package, versionCode, versionName
apksigner verify --print-certs app.apk                # match on "certificate SHA-256 digest:" only; the prefix differs by build-tools version
unzip -o -q app.apk classes.dex -d out && dexdump -d out/classes.dex > dump.txt   # always to a file, then grep
```
