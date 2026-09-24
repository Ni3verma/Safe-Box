# Testing strategy

What is tested where, and why the boundaries are drawn as they are.

## The layers

| Layer | Location | Runs on | Count |
|---|---|---|---|
| Unit | `app/src/test` | JVM | full suite via `:app:testDebugUnitTest` |
| Instrumentation / E2E | `app/src/androidTest` | `debug` build, device or GMD | ~199 tests, ~29 classes |
| Migration | `app/src/androidTest/.../MigrationTest.kt` | device, real SQLite | per-step plus a full v1→current chain |
| Release-build sanity | *not built yet* | `qa` APK, black box | see below |
| Upgrade | *not built yet* | `qa` → `qa`, black box | [upgrade-testing.md](upgrade-testing.md) |

## Principles

**Instrumentation tests run on `debug` only.** Not a preference — a structural constraint. See
[ADR-0001](../decisions/0001-instrumentation-tests-run-on-debug-only.md).

**Selectors are text and content description.** The upside is that the same selectors work from UI
Automator, so black-box tests can drive even the minified QA APK. The downside is that changing
user-visible copy breaks tests — accept that, because the alternative is polluting production with
test hooks.

The one exception is a control with **no text or content description of its own** — the settings
switches and sliders, which sit beside their label. Those carry a `Modifier.testTag` from
`ui/core/TestTags.kt`, published to UI Automator as a resource id through `testTagsAsResourceId`
in `debug` and `qa` builds only (never `release`; `TestTagsTest` pins that). Do not tag anything a
test can already find by text or content description.

**Randomized data over fixed fixtures, where round-trip fidelity is the property under test.** The
backup/restore tests generate fresh random records every run so corner cases surface over time.
They must print enough on failure to reproduce the exact scenario — a seed is not enough on its own,
dump the offending record.

**Dispatchers are injected, never hardcoded.** `DispatchersProvider` makes virtual-time execution
deterministic under `TestDispatcher`. A test that needs a real delay is usually a design smell.

**Analytics are part of the contract.** Every user-visible action, including every dialog
(`*_SHOW`, `*_ALLOW_CLICK`, `*_CANCEL_CLICK`), gets an `AnalyticsKey` and a ViewModel test
asserting `analyticsHelper.logEvent(...)` fires.

## Known coverage gaps

These are deliberate, recorded so nobody re-discovers them as "bugs".

| Gap | Why it is open |
|---|---|
| The minified `qa` APK is never exercised by an automated UI test | [ADR-0001](../decisions/0001-instrumentation-tests-run-on-debug-only.md); black-box suite is the planned fix |
| No upgrade test across app versions | [ADR-0002](../decisions/0002-upgrade-testing-via-black-box-uiautomator.md) |
| Keystore alias continuity is untested | only an upgrade test can cover it — see [persistence-and-crypto.md](../architecture/persistence-and-crypto.md) |
| Restore does not reject a *newer* `BACKUP_VERSION` | pre-existing on `master`; the real-world case (new backup opened by an already-shipped old app) is unfixable from the current codebase |
| CameraX `bindToLifecycle` failure path | considered, not prioritised |

## The failure that looks like a bug but isn't

If an entire instrumentation run times out in `E2ETestUtils.unlockApp`, **check emulator disk
before anything else**:

```bash
adb shell df -h /data
```

A full run costs ~1.2 GB. When `/data` fills, the test APK fails to install, the app never renders,
and every test fails in a way that is indistinguishable from a real UI regression. Details in the
[build-and-test skill](../../.agents/skills/build-and-test/SKILL.md).
