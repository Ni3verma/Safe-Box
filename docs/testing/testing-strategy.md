# Testing strategy

What is tested where, and why the boundaries are drawn as they are.

## The layers

| Layer | Location | Runs on | Count |
|---|---|---|---|
| Unit | `app/src/test` | JVM | full suite via `:app:testDebugUnitTest` |
| Instrumentation / E2E | `app/src/androidTest` | `debug` build, device or GMD | ~199 tests, ~29 classes |
| Migration | `app/src/androidTest/.../MigrationTest.kt` | device, real SQLite | per-step plus a full v1→current chain |
| Upgrade and restore | `upgrade-test/` + `scripts/run-*-test.sh` | `qa` APK, black box, emulator | [upgrade-testing.md](upgrade-testing.md); release gate on every tag |

## Principles

**Instrumentation tests run on `debug` only.** Not a preference — a structural constraint. See
[ADR-0001](../decisions/0001-instrumentation-tests-run-on-debug-only.md).

**Selectors are text and content description.** The upside is that the same selectors work from UI
Automator, so black-box tests can drive even the minified QA APK. The downside is that changing
user-visible copy breaks tests — accept that, because the alternative is polluting production with
test hooks.

The exceptions carry a `Modifier.testTag` from `ui/core/TestTags.kt`, published to UI Automator as
a resource id through `testTagsAsResourceId` in `debug` and `qa` builds only (never `release`;
`TestTagsTest` pins that): a control with **no text or content description of its own** (the
settings switches and sliders), a control whose label **equals a heading** (the Backup and Restore
buttons), and **structure** a black-box test must read as a unit (the records list and its rows).
Do not tag anything a test can already find by text or content description; see
[ADR-0004](../decisions/0004-verify-upgrade-and-restore-by-decoded-backup.md).

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
| The minified `qa` APK is exercised only by the upgrade and restore tests, not by a broad UI suite | [ADR-0001](../decisions/0001-instrumentation-tests-run-on-debug-only.md); those tests run on release tags and labelled PRs |
| Upgrades are tested from N-1 only | [ADR-0004](../decisions/0004-verify-upgrade-and-restore-by-decoded-backup.md); older chains are `MigrationTest`'s job |
| Keystore alias continuity is covered only by the upgrade test | nothing else survives an in-place install — see [persistence-and-crypto.md](../architecture/persistence-and-crypto.md) |
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
