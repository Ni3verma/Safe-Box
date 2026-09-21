---
name: build-and-test
description: Exact commands, environment setup and known traps for building Safe-Box and running its unit, instrumentation and managed-device tests
---

# Building and testing Safe-Box

Use this whenever you need to compile, run tests, or work with a device or emulator.

## Environment

The Gradle wrapper needs a JDK on `PATH`, and a bare shell usually has none on macOS:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>
```

`adb` and the emulator need **network and device access, so they must run outside the sandbox**
(`BypassSandbox: true`). Gradle usually works sandboxed once dependencies are cached; if it fails
on dependency resolution, run it unsandboxed.

Android SDK tooling lives at `~/Library/Android/sdk/build-tools/<version>/` — `aapt2`, `apksigner`,
`dexdump` are all there. `apksigner` is a Java program, so it needs the same `JAVA_HOME`.

## Commands

| Goal | Command |
|---|---|
| All unit tests | `:app:testDebugUnitTest` |
| Build the instrumentation APK | `:app:assembleDebugAndroidTest` |
| All instrumentation tests on a connected device | `:app:connectedDebugAndroidTest` |
| One instrumentation class (**one only** — see Traps) | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` |
| Managed device (no device needed, matches CI) | `:app:pixel8Api34DebugAndroidTest` |
| Coverage (opt-in, slow) | add `-Pcoverage` |
| Lint as CI runs it | `:app:lintRelease` |
| Minified QA APK | `:app:assembleQa` |

### Python tooling

`scripts/check_docs.py` has a stdlib-`unittest` suite. No `pip install` anywhere in this pipeline —
the checker runs in a bare shell, the pre-commit hook and CI, so it must work without a virtualenv.
The machine has Python 3.9.6 and **no pytest**.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py' -v   # 18 tests, ~0.15s
python3 scripts/check_docs.py                               # the checker itself
```

CI runs both, tests first, before the JDK is even installed.

> [!WARNING]
> **Setting `LC_ALL=C` does not give you a non-UTF-8 Python.** PEP 538 coerces the C locale to
> C.UTF-8 and PEP 540 can force UTF-8 mode, so a test that only sets `LC_ALL=C` passes even with the
> encoding bug present. Reproducing it needs `PYTHONCOERCECLOCALE=0` and `PYTHONUTF8=0` as well.
> Caught by deleting the fix and finding the test still green.

## Traps

### `BUILD SUCCESSFUL` does not mean your tests ran

`connectedDebugAndroidTest` exits 0 when the class filter matches **nothing**, and — observed
2026-09-21 — when it matches only *some* of the classes you asked for. A comma-separated filter:

```
-Pandroid.testInstrumentationRunnerArguments.class=a.b.MigrationTest,a.b.ClipboardActionsTest
```

ran `MigrationTest` only, reported `BUILD SUCCESSFUL in 15s`, and never mentioned that the second
class was skipped. **Run one class per invocation**, and always confirm what executed:

```bash
rm -rf app/build/outputs/androidTest-results
# ... run the single-class connectedDebugAndroidTest invocation here ...
python3 -c "
import glob, os, time, xml.etree.ElementTree as ET
files = glob.glob('app/build/outputs/androidTest-results/**/*.xml', recursive=True)
if not files:
    raise SystemExit('NO RESULTS FILE - the run produced nothing, treat as failure')
for f in files:
    age = time.time() - os.path.getmtime(f)
    r = ET.parse(f).getroot()
    print(f, f'age={age:.0f}s', 'tests=', r.get('tests'), 'failures=', r.get('failures'))
    for ts in r.iter('testsuite'):
        print('  ', ts.get('name'), ts.get('tests'))
"
```

> [!WARNING]
> **Delete the results directory before the run.** Gradle does not clear it, so the recursive glob
> will otherwise match an XML from an earlier invocation and report it as though it were this run's
> — the exact silent-success failure this check exists to catch. The age column is the backstop: a
> file older than the run just performed is stale, not evidence.

Note the results file is `TEST-<device name>.xml` and the device name contains **spaces and
parentheses** (`TEST-Pixel_8_API_35(AVD) - 15.xml`), so quote the path. The root element is
`<testsuites>` with one nested `<testsuite>` per class — parsing only the root tag finds nothing.

Never report a test run as passing on the strength of the Gradle exit code alone.

### The emulator runs out of disk and it looks like an app bug

A full instrumentation run consumes roughly **1.2 GB of `/data`**. A default AVD partition survives
about four runs, then installs start failing with:

```
IOException: Requested internal only, but not enough space
```

The instrumentation APK silently fails to install, the app never renders, and every test times out
in `E2ETestUtils.unlockApp` after 25 s — which is indistinguishable from a real UI regression.

**Always check disk before blaming the app:**

```bash
adb shell df -h /data
```

Recreate with headroom:

```bash
emulator -avd <name> -wipe-data -partition-size 8192
```

A fresh wipe sits at ~641 MB used.

### Instrumentation tests only run on `debug`

Do not attempt to run the existing suite against the `qa` (minified) build. It fails with a silent
`tests=0` / `BUILD SUCCESSFUL`. The reason is structural and is written up in
[ADR-0001](../../../docs/decisions/0001-instrumentation-tests-run-on-debug-only.md). Read that
before re-investigating.

### KSP ordering for androidTest

`app/build.gradle` has an explicit `dependsOn("kspDebugAndroidTestKotlin")` wiring for
`compileDebugAndroidTest*` and `dexBuilderDebugAndroidTest*`. If you add a variant or rename a
task, that block needs updating or Hilt's generated test sources go missing.

### `unitTests.returnDefaultValues = true`

Unmocked Android framework calls return defaults instead of throwing in unit tests. Convenient, but
it means a unit test can pass while doing nothing. Prefer asserting on observable state.

## Selector strategy for UI tests

Production code contains **no `Modifier.testTag`**. Everything is found by text or content
description, which is why the existing suite is 449 × `onNodeWithText` and
101 × `onNodeWithContentDescription`.

Useful side effect: those same selectors work from **UI Automator**, so black-box tests can drive
even the minified QA APK. Verified with:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml
```

## Inspecting a built APK

```bash
aapt2 dump badging app.apk | head -1                  # package, versionCode, versionName
apksigner verify --print-certs app.apk                # signing certificate
unzip -o -q app.apk classes.dex -d out && dexdump -d out/classes.dex > dump.txt
```

> [!WARNING]
> `dexdump -d` output is hundreds of thousands of lines. **Always redirect it to a file** and grep
> the file. Never let it land in the conversation.
