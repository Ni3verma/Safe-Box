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
| One instrumentation class | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` |
| Managed device (no device needed, matches CI) | `:app:pixel8Api34DebugAndroidTest` |
| Coverage (opt-in, slow) | add `-Pcoverage` |
| Lint as CI runs it | `:app:lintRelease` |
| Minified QA APK | `:app:assembleQa` |

## Traps

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
