# ADR-0004 — Verify upgrade and restore by decoded backup comparison

- **Status:** Accepted
- **Date:** 2026-09-26
- **Amends:** [ADR-0002](0002-upgrade-testing-via-black-box-uiautomator.md),
  [ADR-0003](0003-ui-labels-from-resource-names.md)

## Context

The first harness (branch `feature/upgrade-test-harness`, PRs #259–#268) seeded the previous
release by typing records into its forms, read the whole vault back off the screens into a text
"oracle", upgraded, read it again and diffed the two. It worked, and it was the wrong shape:

- **Slow.** Typing five records took 145 s of the seed phase; a run made about 75 swipes, rewinding
  and scrolling to each record and scrolling detail screens once per blank field, dumping the
  screen after each swipe (CI run 36127205711). The app's own work was negligible:
  `RestoreDataWorker` 0.2–0.3 s, `BackupDataWorker` 0.4 s.
- **Fragile.** Rows had no node of their own, so they were rebuilt from geometry. Every trap in
  that code fired for real at least once: filter chips read as a record, a nested horizontal
  scroller taken for the list, `UiObject2.scroll()` returning false on every swipe because the list
  never sends scroll events, countdown text making an unmoved list look scrolled.
- **Shallow.** It compared what the screens show, not what is stored. A field no screen renders
  could be lost without failing anything.
- **Hand-maintained expectations** (`LegacyFixture.kt`) for the old-format fixtures.

## Decision

1. **Expected data is a backup file; the verdict is a backup file.** The host decodes both with
   `scripts/InspectBackup.java` into a canonical dump and diffs them exactly.
2. **Upgrade:** only N-1 → N. N-1 is the newest stable release older than the build under test that
   carries a `SafeBox-qa.apk`. N-1 gets its data only by **restoring** `seed.bak` as of N-1's tag.
3. **Restore:** every committed backup format, restored in turn into one install of N, each compared
   with its own file; then N's own backup, a damaged file and a wrong password.
4. **One hand-captured seed** at the fixed path `upgrade-test/seed/seed.bak`; older formats are
   frozen as `upgrade-test/fixtures/format-<k>.bak`. PR-time checks force a `BACKUP_VERSION` bump,
   a new seed, decoder support and the archived file whenever the export classes change.
5. **The screen is checked only for what a user experiences, on N only**, through a small set of
   test tags: the list shows exactly the file's records; one record per type opens, found via
   search.
6. **QA APK only**, and on tag runs the exact artifact being released.
7. **A release gate:** `release.yml` calls the reusable workflow; both jobs must pass before
   `release_on_github`.

## Ruled out

| Option | Why not |
|---|---|
| Screen-text oracles | The previous design: slow, geometry-dependent, blind to unrendered fields (Context) |
| Seeding N-1 by typing records | Forms belong to app tests; the slowest and most fragile step; a restore reaches every field |
| Several baselines (previous, schema boundary, oldest), an oldest-release floor | Skipped releases run the same migrations, which `MigrationTest` covers step by step and end to end. Each extra baseline is another old UI to drive |
| Database version in the format fingerprint | The backup format is the export classes; a schema change that leaves them alone does not change what a backup holds |
| Byte comparison of backups | Fresh random salt and IV per backup, the creation time in the header, and legitimate layout changes between formats |
| Decrypting with the app's own code | A bug shared by writer and checker would cancel out; `InspectBackup` mirrors the parameters independently |
| An app `androidTest` for restore | Runs unminified, so an R8 breakage in restore would pass; running it on `qa` collapses into disabling minification ([ADR-0001](0001-instrumentation-tests-run-on-debug-only.md)) |
| Triggering on `release: published` | A release created with `GITHUB_TOKEN` never triggers other workflows |
| Tagging every element (text fields, tabs, messages) | `By.res` and `By.text` cost the same query, so tags buy no speed; a label lookup already works there and the previous release needs it anyway |
| "Tag, else label" fallback lookups | A missing tag would pass silently through the fallback |
| Downgrade test | Android refuses a lower `versionCode`; the runner checks before installing |
| Clipboard, TOTP code display across the upgrade | Covered by app tests; the upgrade test checks the stored secret, which is what an upgrade can lose |
| A password per fixture | One password (`Upgrade@@Test123`) satisfies every release's rules. `format-2.bak` keeps `Fixture@Backup1` because re-encrypting it needs script-made bytes or a round trip through an old app |

## Consequences

- A format change costs one hand capture, on a debug build of the PR, and nothing else to
  maintain.
- The harness drives four screens on N-1 (sign-up, restore, backup folder, settings) with
  resource-name labels and, for the settings switches, geometry. That code goes once a release
  containing `upgrade-test/seed/` and the tags becomes N-1 (follow-ups in
  [upgrade-testing.md](../testing/upgrade-testing.md#9-risks-limits-and-follow-ups)).
- Only restored records exist before the upgrade. Saving through a form and restoring are two
  different encryption call sites, so a defect only in the form path of N-1 is not seen here; it is
  accepted because both paths encrypt under the same Keystore key, which is what an upgrade can
  lose, and forms are covered by app tests.
- The release waits for two emulator jobs on every tag.
