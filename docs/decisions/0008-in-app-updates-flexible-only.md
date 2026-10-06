# 8. In-app updates use the FLEXIBLE flow only, with a lock-aware restart

Date: 2026-09-28

Status: Accepted

## Context

Issue [#85](https://github.com/Ni3verma/Safe-Box/issues/85): users had to open Play Store to update.
Play's [in-app update API](https://developer.android.com/guide/playcore/in-app-updates) has two flows:

- **FLEXIBLE:** a consent sheet, a background download, and a restart that the app triggers.
- **IMMEDIATE:** a full-screen Play UI that blocks the app until the update is installed.

Safe-Box is a password vault, so two properties set it apart from a typical app:

- Blocking the UI blocks access to credentials, often right when the user needs them.
- The vault has a lock state, and a process restart ends the session. The session ends through the
  away timeout (`ActiveSessionManager`) and a forced re-login.

## Decision

1. **FLEXIBLE only.**
   - Choosing IMMEDIATE is just an option on `AppUpdateOptions`, not something `updatePriority`
     gates. The real question is what would *trigger* it, and nothing does today:
     - Priority can only be set through the Play Publishing API. CI does not publish to Play; the
       AAB is uploaded by hand. *(No longer true since 2026-10-06: `release_on_play` uploads every
       tag to the internal track, [ADR-0009](0009-play-upload-internal-track.md), so
       `inAppUpdatePriority` is settable. The decision stands; the trigger below is unchanged.)*
     - No release has needed to force users off an old version. Room migrations run on any upgrade
       path, so skipping versions is safe.
2. **Prompt only while the vault is unlocked**, meaning the root nav is in `HomeGraph` and the away
   timeout has not fired. The login screen launches `BiometricPrompt` by itself, and Play's sheet
   would compete with it.
3. **Ask at most once per Play version.**
   - The offered `availableVersionCode` is persisted when the consent sheet is **launched**,
     whatever the user answers.
   - The user is asked again only for a higher version.
   - A per-process guard also caps the sheet at one launch per process.
4. **Restart when unlocked, install silently when locked.**
   - A downloaded update shows an indefinite snackbar with a Restart action and a close (✕) while
     unlocked. A restart discards unsaved edits and forces a new login, so it must be the user's
     choice.
   - While locked, `completeUpdate()` runs immediately. The away timeout counts as locked, so an
     update that finished downloading in the background installs silently at that moment.
5. **The session timer is not paused** around Play's consent sheet.
6. **`IN_APP_UPDATE_ENABLED` is on in `release` only.** `debug` and `qa` get
   `NoOpAppUpdateController`.

## Why

- **No timer pause (decision 5).** `ActiveSessionManager` clears `isPaused` only in `onStart` of
  `ProcessLifecycleOwner`.
  - The flexible sheet is translucent, so the process never stops and never restarts. A pause would
    stick, and the *next* real backgrounding would never start the auto-lock timer.
  - Pressing Home while the sheet is open would leave the vault unlocked in the background for as
    long as the user stays away.
  - Not pausing costs at most a re-login, and only if the sheet turns out to be opaque and the user
    takes longer than the away timeout. Nothing is lost, because the result launcher sits above the
    `NavHost`: the consent result still arrives and the download continues.
  - Chosen by the user on 2026-09-28 over "pause, then unpause on result", which still left the
    Home-press hole.
- **Persist at launch (decision 3).** An earlier plan persisted only on cancel, which left two loops
  open:
  - A device where the flow always fails would re-prompt, and log the prompt, on every launch.
  - A user who accepted and then cancelled the download from the notification would be asked again.
- **Release only (decision 6).** Play only serves updates to the application id it distributes. The
  `.debug` and `.qa` ids can never succeed, and keeping `qa` free of Play calls keeps `:upgrade-test`
  deterministic. `qa` sets the flag explicitly to `false`, because `initWith(release)` would otherwise
  copy `true`.

## Evidence

Behaviour of `app-update-ktx` 2.1.0 `requestUpdateFlow()`, read from the bytecode with
`javap -c` on 2026-09-28. `AppUpdateResult.toAppUpdateState()` in
`controller/AppUpdateResultMapping.kt` depends on it.

| `updateAvailability` | `installStatus` | Emits |
|---|---|---|
| `UNKNOWN` | any | closes with `InstallException(-2)` |
| `UPDATE_NOT_AVAILABLE` | any | `NotAvailable`, then completes |
| `UPDATE_AVAILABLE` / `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` | `DOWNLOADED` | `Downloaded`, then completes |
| same | anything else | `Available(info)`, then `InProgress` / `Downloaded` from a listener, which completes on a terminal status |

This means a check that lands mid-download reports **`Available`** with status `DOWNLOADING`. So the
mapper looks at the install status before treating a result as a fresh offer.

`FakeAppUpdateManager.startUpdateFlowForResult(info, launcher, options)` never uses the launcher. It
only sets `isConfirmationDialogVisible`. So instrumentation tests can assert on that flag, but can
never receive a consent result.

## Consequences

- Revisit IMMEDIATE only when there is a real forced-update need, such as a crypto or security fix.
  The trigger would be `clientVersionStalenessDays` or a minimum version from Remote Config, or CI
  publishing through the Play Publishing API, which would make `updatePriority` usable.
  - The change is incremental: one `startImmediateUpdate` on `AppUpdateController`, one ViewModel
    branch, and an `onResume` check that resumes `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`.
- A real Play round trip cannot be tested in CI. It is covered by the manual procedure in
  [in-app-updates.md](../architecture/in-app-updates.md#manual-test-with-real-play).
- Design, state flow and analytics bounds: [in-app-updates.md](../architecture/in-app-updates.md).
