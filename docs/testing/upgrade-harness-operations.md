# Upgrade harness — operations

How to run, extend and debug the upgrade and restore tests. What they prove and why is in
[upgrade-testing.md](upgrade-testing.md); this file is what you need when a run fails or the backup
format changes.

## What exists

| Piece | Path | Responsibility |
|---|---|---|
| Gradle module | `upgrade-test/` | builds the instrumentation APK. **Assembled only, never run, by Gradle** |
| Category 1 steps | `upgrade-test/.../UpgradeTest.kt` | `prepareOnPreviousRelease`, `verifyAfterUpgrade` |
| Category 2 steps | `upgrade-test/.../RestoreTest.kt` | `setUpFreshInstall`, `restoreIntoEmptyVault`, `restoreOverVault`, `failedRestoresLeaveVaultUntouched` |
| Screen check | `upgrade-test/.../RecordsCheck.kt` | list equals the file's rows; one record per type opens via search |
| App driver | `upgrade-test/.../SafeBoxApp.kt` | launch, sign-up, unlock, backup folder, restores, refusals |
| Runners | `scripts/run-upgrade-test.sh`, `scripts/run-restore-test.sh` | install, push files, run one method per phase, pull and compare backups |
| Shared host code | `scripts/lib/harness.sh` | device choice, emulator guard, QA APK and signer check, `run_phase`, comparisons |
| Decoder | `scripts/InspectBackup.java` | `--canonical`, `--rows`, `--header`, `--supported-version` |
| N-1 | `scripts/resolve-previous-release.sh`, `scripts/fetch-baseline-apk.sh` | pick N-1 and its seed; download its QA APK |
| Seed tooling | `scripts/update-seed.sh` | install a new seed, archive the old format, regenerate READMEs |
| PR-time checks | `scripts/tests/backup-format-test.sh` | format lock, seed, decoder, archived formats |
| Zero-test guard, crash sentinel | `scripts/lib/instrumentation-guard.sh`, `crash-sentinel.sh` | decide whether a phase really ran, and whether the app crashed |
| Workflow | `.github/workflows/upgrade-test.yml` | build, then the two categories on two emulators |

Every script under `scripts/tests/` runs in `ci.yml` without an emulator.

The module is a self-instrumenting `com.android.test` module with **no compile dependency on
`:app`** — `:upgrade-test:assembleDebug` runs zero `:app` tasks. Do not add one; that coupling is
exactly what ADR-0001 says breaks against minified builds.

## Running it

From the repository root, against `emulator-5554`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SERIAL=emulator-5554
./gradlew assembleQa :upgrade-test:assembleDebug

# Category 2: no network needed
./scripts/run-restore-test.sh app/build/outputs/apk/qa/SafeBox-qa.apk

# Category 1: needs gh and network for N-1
tag=$(./scripts/resolve-previous-release.sh HEAD previous/)
./scripts/fetch-baseline-apk.sh "$tag" previous/
./scripts/run-upgrade-test.sh previous/SafeBox-qa.apk previous/previous-seed.bak \
    app/build/outputs/apk/qa/SafeBox-qa.apk
```

`JAVA_HOME` (or a real `java` on `PATH`) is needed because the host decodes backups with
`InspectBackup.java`; macOS's `/usr/bin/java` is only a stub. A local QA build has versionCode
9999999, so it always supersedes N-1.

Output lands in `upgrade-test-out/upgrade/` and `upgrade-test-out/restore/` (override with the last
argument), collected even on failure:

| File | What |
|---|---|
| `instrumentation-<phase>.txt` | raw `am instrument` output of one phase |
| `crash-scan-<phase>.txt` | crash and system log buffers after that phase |
| `after-<label>.bak` | the backup N took after that phase |
| `<label>-expected.txt`, `<label>-actual.txt`, `<label>.diff` | canonical dumps of the restored file and of that backup, and their diff |
| `damaged.bak` | the truncated seed the refusal phase restores |
| `logcat.txt` | the whole device log |

Each phase prints its duration; use those, not guesses, when making a step faster.

> [!CAUTION]
> Both runners **uninstall `com.andryoga.safebox.qa`** and sign up from scratch, so they must not
> be pointed at a handset. The target is resolved once and exported as `ANDROID_SERIAL`, and a
> target whose `ro.build.characteristics` lacks `emulator` is refused unless
> `UPGRADE_TEST_ALLOW_PHYSICAL=1` is set. With two devices attached, an unpinned run stops and
> lists them.

> [!NOTE]
> The CI emulator is **API 34**, `default` image, `pixel_6` profile; the local one is a Pixel 8 on
> **API 35** with Google APIs. A local pass does not imply a CI pass.

### In CI

- **Release:** automatic on every `v*` tag, gating `release_on_github`. One re-run is allowed for a
  suspected flake; a second failure is a real failure.
- **Pull request:** add the label **`run-upgrade-test`**. Removing and re-adding it runs again.
- **Manual:** dispatch **Upgrade & Restore Test** (only once the workflow is on `master`: GitHub
  offers dispatch only for workflows on the default branch).

Artifacts: `upgrade-test-output-<N-1 tag>` and `restore-test-output`, the directories above.

### Re-running one phase

After a late failure the device is usually still in the state that phase starts from, so the phase
can be repeated on its own with the `-e` arguments its `run_phase` line in the runner passes. For
example, a restore over the vault:

```bash
adb shell am instrument -w -r \
  -e class com.andryoga.safebox.upgradetest.RestoreTest#restoreOverVault \
  -e restoreFile restore-test-seed.bak -e restorePassword 'Upgrade@@Test123' \
  -e expectedRows "$(java scripts/InspectBackup.java --rows upgrade-test/seed/seed.bak 'Upgrade@@Test123' | base64 | tr -d '\n')" \
  com.andryoga.safebox.upgradetest/androidx.test.runner.AndroidJUnitRunner
```

This bypasses the zero-test guard, the crash sentinel and the backup comparison, so it is only for
iterating; evidence comes from a full run.

## Adding a step

A phase is one `@Test` method plus one `run_phase` line in a runner.

1. Write the method in `UpgradeTest` or `RestoreTest`. Read arguments through
   `SafeBoxApp.argument(name)`; the host is their single owner.
2. Add `run_phase <label> <Class#method> -e <name> <value> …` in the runner, in the position the
   step needs relative to `adb install -r`.
3. If the phase backs up, call `empty_backup_dir` before it and `pull_single_backup` plus
   `assert_same_records` after it.

Phases are invoked one method at a time by `-e class <fqcn>#<method>`. That is not stylistic: a
comma-separated filter silently runs only the first class, and a filter matching nothing exits 0
printing `OK (0 tests)`. `assert_instrumentation_ran` makes both fatal. **Never add a phase that
bypasses it.**

## Capturing a seed

Needed when `backup-format-test.sh` says the seed is not in the current format — that is, in the
PR that bumps `BACKUP_VERSION`.

1. Build a **debug** APK of that PR: `./gradlew assembleDebug`. Not QA: the build type does not
   affect the file (the export classes are `@Keep @Serializable`, their serial names are fixed at
   compile time, and `BACKUP_VERSION` is a constant), and a debug build is much faster. Found
   2026-09-26 capturing the first format-3 seed.
2. Install it on `emulator-5554` with no older Safe-Box debug install, and sign up with master
   password `Upgrade@@Test123`.
3. Enter records per the checklist:
   - every record type the build supports;
   - per type, one record with **every** field filled and one with only the mandatory fields;
   - one note with emoji, Hebrew or Arabic and accented letters, one note of about 4000
     characters, one multi-line note with a blank line, a tab, quotes and a backslash.
   Values must be invented, never personal: they are committed. The emulator shares the Mac
   clipboard (Extended controls → Settings), so paste rather than type.
4. Backup & Restore → choose a backup folder → **Back up**, backup password `Upgrade@@Test123`.
5. `adb pull /sdcard/<folder>/SafeBoxBackup….bak`, then
   `scripts/update-seed.sh <file> "debug build of PR #<n> (<sha>)"`. It refuses a file that does
   not decode with the fixed password or is not in the current format, archives the outgoing seed
   as `upgrade-test/fixtures/format-<k>.bak`, and regenerates both READMEs.
6. Commit the seed, the fixture, both READMEs, the lock line and the `BACKUP_VERSION` bump together.

If the new format needs decoder changes (a new JSON key, header or crypto change), extend
`InspectBackup.java` and raise `SUPPORTED_BACKUP_VERSION` in the same PR; check 3 enforces it.

## Writing selectors

The previous release contains none of the harness's tags and the APK under test is minified, so
anything used on N-1 is found by visible text or content description, held as an Android
**resource name** and resolved against whichever build is installed by
[AppStrings](../../upgrade-test/src/main/java/com/andryoga/safebox/upgradetest/AppStrings.kt), per
[ADR-0003](../decisions/0003-ui-labels-from-resource-names.md). Tags are used on N only, and only
where [upgrade-testing.md §7.3](upgrade-testing.md#73-test-tags-and-the-records-check-n-only) allows.

| Trap | Detail |
|---|---|
| Add a label as a resource name, never as text | `app.label("user_id")`, not `"User Id"`. For a step on N-1, the name must exist in **N-1's** `strings.xml` (`git show <tag>:app/src/main/res/values/strings.xml`). |
| Mandatory field labels carry an asterisk | `MandatoryLabelText` appends `*` **inside the same text node**, and no resource carries it. Use `app.formLabel(name, isMandatory = true)`. |
| Resource text is not rendered text | `user_id` is `"User Id "` with a trailing space. `AppStrings` trims; do not bypass it with a raw `getString`. |
| Label and input are different nodes | Find the label, then search its parent for an `EditText`. |
| `By.text(String)` is an exact match | It quotes the argument, so `Password*` is safe to pass literally. |
| Diffing two snapshots picks up SystemUI | The status-bar clock changes on the minute. Scope any before/after diff to the app's package, as `UnlockScreen` does. |
| Masked password fields | `UiSupport.retypeMasked` waits for the bullet count to equal the new value's length, so the wrong and right test passwords must differ in length. |

Every lookup must fail with the window hierarchy attached. `UiSupport.describeScreen()` does this;
use it in every new failure message.

## Launching the app

The launch intent is resolved through `PackageManager`, which needs the `<queries>` entry in
`upgrade-test/src/main/AndroidManifest.xml`. If the app's `applicationId` changes, that entry must
change with it.

> [!IMPORTANT]
> **Never wait on `By.pkg(APP_PACKAGE).depth(0)` to decide the app is up.** On a device with an
> enrolled fingerprint, the unlock screen raises a SystemUI biometric sheet the instant it appears,
> so the app stops being the foreground package while running perfectly. `SafeBoxApp` waits on the
> expected screen's text and, on timeout, presses back to dismiss the prompt; the app falls back to
> the password field. Back can also send the task home on gesture navigation, so each of the three
> attempts re-issues the launch intent. CI has no biometric enrolled: these flakes reproduce **only
> locally**. Do not delete the recovery path because CI is green.

## The document pickers

**Files are pushed by the host, not the test.** Instrumentation can only write app-private
storage, which the picker cannot see. The runners push to `/sdcard/Download` and ask MediaProvider
to rescan, because DocumentsUI lists that root from the media database.

**Pushed files are touched.** `adb push` keeps the host file's mtime, and both *Recent* and
*Downloads* sort newest first with no scrolling in the helper. Untouched, `format-2.bak` (committed
days earlier) was missing from *Recent* and below the fold of *Downloads* on an API 35 emulator,
2026-09-26. `push_to_downloads` in `scripts/lib/harness.sh` touches after pushing.

**The file picker does not reliably open where the file is.** It opens on *Recent*, which sometimes
lists the file and sometimes shows `No items`.
[SafDocumentPicker](../../upgrade-test/src/main/java/com/andryoga/safebox/upgradetest/SafDocumentPicker.kt)
takes the file where it is, otherwise opens the roots drawer and chooses `Downloads`. A passing run
says little about that fallback: when *Recent* lists the file the drawer never opens. To test a
change there, force the path and confirm from logcat that the forced branch ran.

> [!WARNING]
> **DocumentsUI has two package names**: `com.google.android.documentsui` on Google APIs images
> (local) and `com.android.documentsui` on plain AOSP (CI). Every picker selector accepts both.
> Pinning one makes the picker "never open" on the other.

> [!CAUTION]
> **Do not run on an ATD image.** ATD strips DocumentsUI; `ACTION_OPEN_DOCUMENT` then resolves to
> `com.android.fakesystemapp` on a 320x640 screen (run
> [35863723921](https://github.com/Ni3verma/Safe-Box/actions/runs/35863723921)).

**The folder picker** (`ACTION_OPEN_DOCUMENT_TREE`) has no roots drawer and confirms with
`USE THIS FOLDER` then `ALLOW`. Android refuses a tree over the storage root or `Download` — the
button then silently does nothing — so the runners create `/sdcard/SafeBoxUpgradeTest` and pass it
as `backupDir`. It needs no rescan. Between phases it is emptied, not removed, so the app's
persisted grant stays valid.

## The accessibility cache

> [!CAUTION]
> **Never use `UiDevice.wait`/`Until` in this harness.** They match against the accessibility tree
> cached in the *test* process, which is not reliably invalidated when the app navigates: the
> harness once saw the records list under the previous screen's app bar for 60 s. Every lookup goes
> through `UiSupport.findOrNull`/`awaitObject`/`awaitGone`, which flush the cache before each poll
> (`flushAccessibilityCache`).

The opposite failure: a handle can be stale by the time it is used, raising
`StaleObjectException`. No call site holds a handle across two operations: taps go through
`clickText`/`clickObject`, typing through `typeInto`, and a read-then-act pair through
`retryingOnStale`.

`adb exec-out screencap` is useless (the app sets `FLAG_SECURE`: every screenshot is black), and
`adb shell uiautomator dump` refuses while instrumentation holds the accessibility connection. The
failure message's hierarchy is the evidence.

## The records list

- **"Data has been successfully restored." is not a completion signal.** The list is still
  catching up when the dialog appears. `RecordsCheck` repeats whole walks until the rows match or
  20 s pass.
- **`UiObject2.scroll()`'s return value is meaningless here:** the list never sends scroll events,
  so it is false on every swipe. The walk ends when the visible rows stop changing, capped at ten
  swipes.
- **The top bar collapses** while the list is scrolled down, and search lives in it. `RecordsCheck`
  rewinds to the top before searching.
- **Open a record by its title, not its row.** `click()` taps a node's centre, which on an
  authenticator row is the one-time-code badge; the tap copies the code and the detail screen
  never opens (API 35, 2026-09-26).
- **A notification-permission rationale** blocks the records screen on every cold start once a
  backup folder is set, unless `POST_NOTIFICATIONS` is granted. The runners grant it right after
  installing (API 33+); runtime grants survive `install -r`. Do not replace this with "dismiss it if
  shown": that is a race.

## What the runners already guarantee

Do not re-implement these in a test:

- both APKs declare `com.andryoga.safebox.qa` and are signed with the pinned QA certificate;
- N's `versionCode` is strictly higher than N-1's, checked before anything is installed;
- the upgrade is `adb install -r` with **no `-d`**, after `am force-stop` — never `pm clear`, never
  `uninstall`;
- `firstInstallTime` is unchanged and `versionCode` is N's after the upgrade: `/data` survived;
- every phase executed exactly the one test it selected, and it passed;
- the app did not crash or ANR during any phase, scanned even when the phase failed;
- each backup phase left exactly one backup file.

## Triage

| Symptom | Cause |
|---|---|
| `0 test(s) executed, expected at least 1` | the `-e class` filter matches nothing — a renamed or misspelled method |
| `instrumentation component could not be started` | the harness APK is not installed, or the runner name drifted from the manifest |
| `no launch intent for com.andryoga.safebox.qa` | app not installed, or the `<queries>` entry no longer matches its applicationId |
| `declares applicationId '…', expected 'com.andryoga.safebox.qa'` | a debug or release APK was passed; only qa → qa can upgrade |
| `is signed with certificate '…'` | not a QA build of this repository (a fork, a rotated key, or a local build with another keystore) |
| `N (x) does not supersede N-1 (y)` | on CI, a rebuild without the `GITHUB_RUN_NUMBER` override; locally, an old QA APK passed as N |
| `no stable release with a SafeBox-qa.apk is older than …` | tag misspelled, or the release list is empty for this token |
| `<tag> has no upgrade-test/seed/seed.bak and no fallback row` | a release cut before the seed existed became N-1; add a row in `scripts/lib/previous-release.sh` |
| `firstInstallTime changed` | the install replaced the app instead of upgrading it |
| `expected one backup in /sdcard/SafeBoxUpgradeTest …, found N` | 0: the backup never ran or the folder grant was lost across the upgrade. 2+: auto-backup is on |
| `<label>: the backup taken after the restore differs` | a real data difference; `-` lines are the restored file, `+` lines N's backup. Read `<label>.diff` |
| `FATAL: com.andryoga.safebox.qa crashed …` | read `crash-scan-<phase>.txt`; `AEADBadTagException` means the Keystore key was lost — see [persistence-and-crypto.md](../architecture/persistence-and-crypto.md) |
| The hierarchy in a failure is the launcher, not the app | the app was sent home during biometric recovery — see [Launching the app](#launching-the-app) |
| A picker "never opened" | package name (see [The document pickers](#the-document-pickers)) or an ATD image |
| Timeouts after four or five local runs | the emulator's `/data` is full; see the build-and-test skill |
