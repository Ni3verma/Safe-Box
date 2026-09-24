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

**`git commit` inherits both constraints**, because the pre-commit hook shells out to Gradle. Run it
as `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" git commit ...` and
outside the sandbox. Neither failure names the hook as the cause: without `JAVA_HOME` it reports
`Unable to locate a Java Runtime`, and inside the sandbox it reports
`Could not connect to the Gradle daemon` followed by twenty lines of daemon log. (Verified
2026-09-21.)

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

### Documentation checks

Two checks, in two places, neither needing Gradle:

| Check | Where | Command |
|---|---|---|
| Broken relative links | CI (`lycheeverse/lychee-action`) | `lychee --offline '.agents/**/*.md' 'docs/**/*.md' 'upgrade-test/**/*.md'` |
| Stray agent markup, Windows link targets | CI | `git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'` |
| Same, limited to what you staged | pre-commit hook | `git grep --cached -nE "$DOCS_POLICY_PATTERN" -- "${files[@]}"` |

`DOCS_POLICY_PATTERN` is defined in `CICD/gitHooks/pre-commit.sh`, so it is **not set in your
shell**. Load it from there before running either command by hand — sourcing the hook is not an
option, it would run the whole thing:

```bash
DOCS_POLICY_PATTERN=$(sed -n "s/^DOCS_POLICY_PATTERN='\(.*\)'$/\1/p" CICD/gitHooks/pre-commit.sh)
git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'
```

Exit 1 with no output means clean. Skipping the first line does not silently pass: `git grep -nE ""`
aborts with `fatal: command line, '': empty (sub)expression`.

The hook builds `files` from `git diff --cached --name-only --diff-filter=ACMR -z -- '*.md'`, so it
only ever sees this commit's markdown — a violation someone else left in an untouched file is CI's
problem, not yours.

> [!WARNING]
> Never paste the literal pattern into a markdown file. It contains the very strings it searches
> for, so the file immediately matches itself and CI fails on the documentation describing the
> check. Refer to it by name, or extract it as above.

lychee is **not installed on this machine** and there is no `brew`, `cargo` or `npm` to install it
with. Grab the prebuilt binary if you need to check links locally:

```bash
curl -sSL -o lychee.tar.gz \
  https://github.com/lycheeverse/lychee/releases/download/lychee-v0.24.2/lychee-aarch64-apple-darwin.tar.gz
tar xzf lychee.tar.gz && ./lychee-aarch64-apple-darwin/lychee --version
```

> [!NOTE]
> lychee treats a Windows drive-qualified target such as `C:\docs\a.md` as a `c:` **URI scheme**
> and reports it `EXCLUDED` with exit 0 — it is skipped, not checked. `--include '.*'` does not
> override this and there is no `--fail-on-unsupported`. That single case is why the grep exists
> alongside lychee rather than being deleted with the rest. (Verified 2026-09-21 against v0.24.2.)

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

Everything is found by text or content description, which is why the existing suite is
449 × `onNodeWithText` and 101 × `onNodeWithContentDescription`. The only `Modifier.testTag`s are
on controls with no text of their own (the settings switches and sliders, `ui/core/TestTags.kt`).
In `debug` and `qa` they are also visible to UI Automator as resource ids (`By.res(tag)`); in
`release` they are not.

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
