---
name: release-and-ci
description: How Safe-Box is versioned, signed, built and released through GitHub Actions, including where old QA APKs are archived and how to fetch them
---

# Release and CI

## Workflows

| File | Trigger | What it does |
|---|---|---|
| `ci.yml` | push / PR | lint, unit tests, build |
| `nightly.yml` | 02:00 UTC daily + manual | `pixel8Api34DebugAndroidTest` across **3 shards** |
| `release.yml` | push of a `v*` tag | the full release pipeline (below) |
| `upgrade-test.yml` | called by `release.yml`; PR label `run-upgrade-test`; manual | upgrade (N-1 → N) and restore tests on two emulators |
| `run-ui-test.yml` | manual / reusable | on-demand UI test run |
| `gemini-pr-review.yml` | PR | automated bot review |

## `release.yml` job graph

```
tag_db_version → quality ──┬─ debug_pipeline    → SafeBox-debug.apk (artifact, 7d)
                           ├─ release_pipeline  → app-release.aab + Crashlytics symbols
                           └─ qa_pipeline       → SafeBox-qa.apk + mapping.txt
                                     │
                                     ├─ upgrade_test      → upgrade-test.yml on that exact APK
                                     │
                                     └─ release_on_github → needs qa, release and upgrade_test
                                               │
                                               └─ release_on_play → app-release.aab to Play's internal, closed + open testing tracks
```

`quality` runs lint, unit tests, `pixel8Api34DebugAndroidTest`, and an **NDK gatekeeper** that
fails the build if the runner lacks the exact `ndkVersion` from `app/build.gradle`.

**A failing `upgrade_test` blocks the release**, RC or stable; one re-run is allowed for a suspected
flake. It is a `workflow_call` rather than an `on: release` trigger because a release created with
`GITHUB_TOKEN` never triggers other workflows. N-1 excludes the tag under test (and, for an RC, the
stable version it previews), so a re-run after publishing picks the same N-1. `release_on_play`
runs last, so Play never holds a build GitHub does not; see [Play upload](#play-upload).

## Versioning

```groovy
def version_code = (System.getenv("GITHUB_RUN_NUMBER") ?: "9999984").toInteger() + 15
```

- Local builds get `9999999`, so a local APK installs over any CI build **while
  `GITHUB_RUN_NUMBER` stays below 9999985**. That is the entire practical range, but it is a
  consequence of the sentinel rather than a guarantee — do not rely on it in a test assertion.
- `versionName` is derived from `GITHUB_REF_NAME`: `v2.0.4.0` → `2.0.4.0`; a branch → `<branch>-build.<code>`; nothing → `LOCAL-build`.
- Tag format is **`vMAJOR.MINOR.DBVERSION.FIX`** (optionally `-rcN`). The third component tracks the
  **Room schema version**, so bumping the DB requires bumping it in the next tag. Nothing in the
  build reads it (`versionName` is just the tag without its `v`), which is why it is gated:
  - `release.yml` job **`tag_db_version`** runs `scripts/lib/tag-db-version.sh "$GITHUB_REF_NAME"`
    before anything else, and `quality` (hence every other job) needs it. It requires the tag's
    DBVERSION to equal the literal `version = N` in `SafeBoxDatabase.kt`'s `@Database`, and
    `app/schemas/…/N.json` to exist and declare `"version": N`. Other `v*` shapes (`v1.1.0`,
    `-beta`) are rejected.
  - On failure nothing has been built or published. The error names the corrected tag; re-tag
    **the same commit** with it: `git tag <fixed> '<tag>^{commit}'`, not a bare `git tag <fixed>`
    (which tags local HEAD, possibly moved since `<tag>` was cut), then push it and delete `<tag>`.
  - It reads the annotation, **not** the highest schema file. A `5.json` was committed in `331ee64`
    (January 2026) while the database stayed at 4, and was only regenerated for the real v5 by #241
    (different `identityHash`). `v2.0.4.0` therefore ships a `5.json` but DB 4, and "newest
    schema" is wrong for it. Verified 2026-09-24: `v1.7.4.2`, `v2.0.4.0` and `v2.1.4.0-rc3` all
    pass; HEAD is on 5, so the next release must be `v2.x.5.y`.
  - `ci.yml` runs `scripts/tests/tag-db-version-test.sh` on every PR, including a case that parses
    the real source, so replacing the literal with a constant fails in that PR, not at release.

> [!WARNING]
> `GITHUB_RUN_NUMBER` counts runs of **one workflow**, not of the repository. A workflow added by a
> pull request starts at 1, so its builds get `versionCode` 16 while released builds are in the
> twenties. Anything that installs a freshly built APK over a released one has to override it —
> which is why `upgrade-test.yml` rebuilds with `GITHUB_RUN_NUMBER=9999984` on PR and manual runs.
>
> **`env:` cannot do it.** It is a default variable, and the docs say "if you attempt to override
> the value of one of these default variables, the assignment is ignored" — silently. Observed
> twice on 2026-09-22: a build came out as versionCode 16, and after adding
> `env: GITHUB_RUN_NUMBER: 9999984` as 17, the value simply tracking the run number. Assign it on
> the command instead, where it is an ordinary child-process variable:
>
> ```yaml
> run: GITHUB_RUN_NUMBER=9999984 ./gradlew assembleQa
> ```
>
> When checking such an override locally, pick a value that is **not** the fallback. `9999984`
> produces `versionCode` 9999999 — which is exactly what setting nothing produces, so a green
> result proves nothing about whether the variable arrived. Use a distinguishable one:
> `GITHUB_RUN_NUMBER=5000 ./gradlew :app:help -q` printing `Building SafeBox: LOCAL-build (5015)`
> does prove it, including that the value survives the Gradle daemon. Verified 2026-09-22.

## Signing

| Build type | Properties file | In CI as |
|---|---|---|
| `release` | `releaseKeyStore.properties` + `app/releaseKeyStore.jks` | `RELEASE_KEYSTORE_PROPERTIES`, `BASE_64_RELEASE_KEYSTORE` (GPG, `GPG_PASSPHRASE`) |
| `qa` | `nonProdReleaseKeyStore.properties` | committed config, stable key |

Both keystores and their properties files (`app/releaseKeyStore.jks`, `app/nonProdReleaseKeyStore.jks`,
`releaseKeyStore.properties`, `nonProdReleaseKeyStore.properties`) are committed **on purpose as
dummies** so local builds sign; do not flag them as leaked secrets. The real release key reaches CI
only through the secrets above (owner confirmed 2026-09-26; `git ls-files | grep -i keystore`).
`google-services.json` is also GPG-encrypted in CI.

The QA certificate has been **stable since at least `v1.4.4.0`**:
SHA-256 `257ab2043588f0b355bba6a9c9f199c088f079f6306536cd4c94fc2eba7b113d`.
That stability is what makes APK-over-APK upgrade testing possible. It is pinned as
`QA_CERT_SHA256` in `scripts/lib/harness.sh`, and both runners reject an APK signed otherwise.

## Artifact naming

```groovy
output.outputFileName.set("SafeBox-${variant.name}.apk")
```

The workflow files reference these paths literally. **Renaming the output breaks CI silently at
the upload step.** There is already a comment in `build.gradle` saying so.

## The APK archive

`release_on_github` attaches everything it downloaded, so every tag **cut after the upload step
existed** carries:

- `SafeBox-qa.apk` — minified, release-like, stably signed
- `app-release.aab` (since `v2.1.4.0-rc1`)

This is a **permanent, addressable archive of every shipped QA build** from `v1.3.3.0` onwards. Of
18 releases, 15 carry a QA APK; the three that do not (`v1.0.0`, `v1.1.0`, `v1.2.2.0`) predate the
upload step, so the oldest *archived* APK is `v1.3.3.0`. Two other facts are easy to confuse with it:

- the upgrade test only ever upgrades from **N-1**, the newest stable release older than the tag
  under test (`scripts/lib/previous-release.sh`); a release that disappears from the archive
  silently moves N-1 back, so keep attaching `SafeBox-qa.apk` to every release;
- the oldest release that can **write a backup** is **`v1.4.4.0`**; backup/restore arrived in #111,
  and `v1.3.3.0` has no such feature (verified 2026-09-25 with `git grep -il backup v1.3.3.0`).

## Bundle size

Compare the **per-device download**, not the `.aab` file size: the bundle carries all four ABIs and
a non-shipping `BUNDLE-METADATA/` (the R8 map), so it overstates growth badly. There is no
`bundletool` CLI on this machine; estimate from `unzip -lv` compressed entry sizes — base `dex` +
`res` + `assets` + `root` + `resources.pb` + one `lib/<abi>/`. Measure on `bundleQa`, which shares
release's R8 config; `bundleRelease` would upload a mapping file to Crashlytics. Data point (arm64,
2026-10-03): bundled ML Kit barcode scanning raised the download from 3.09 MB to 6.29 MB; ZXing
`core` brought it back to 3.39 MB
([ADR-0005](../../../docs/decisions/0005-qr-decoding-with-zxing-core.md)).

## GitHub tooling

`gh` is installed and authenticated. What the token may do (reads, issues), what it may not (push,
merge, labels), the unauthenticated fallback, and why a new `workflow_dispatch` workflow cannot be
run before it merges: [github-tooling skill](../github-tooling/SKILL.md). Both `gh` and `curl` need
network, so they run **outside the sandbox**. Used by the procedures here:

```bash
gh release download v2.0.4.0 -p 'SafeBox-qa.apk' -D old/     # fetch a baseline APK
gh run list --workflow=release.yml --limit 5                 # recent release runs
gh run watch <run-id>                                        # follow a run to completion
```

## Crashlytics

`mappingFileUploadEnabled` is deliberately **false** for `qa`, because it is an indirect input to
the minify task and would defeat incremental builds. If a qa stack trace needs deobfuscating,
upload `app/build/outputs/mapping/qa/mapping.txt` manually.

## Play upload

Decision and alternatives: [ADR-0009](../../../docs/decisions/0009-play-upload-testing-tracks.md).
`release_on_play` uploads the `app-release.aab` artifact — the same file the GitHub release carries —
to the **internal (`internal`), closed testing (`alpha`) and open testing (`beta`)** tracks as completed
releases, in one edit, on every `v*` tag, RC and stable. Committing the edit sends the closed and open
testing tracks for review by itself (`internal` requires no review); the API cannot hold them for a
manual *Send for review* (the flag for that is only accepted when Play already has un-sent changes
queued, see the ADR). Nothing here reaches production.

### One-time setup (owner only; the agent cannot do any of it)

1. **GCP** — in the Firebase project's GCP console (or any project): enable the *Google Play Android
   Developer API*; create a service account with no GCP roles; create a JSON key for it.
2. **Play Console** — *Users and permissions → Invite new users*, the service account's email,
   app permission on Safe-Box: **Release apps to testing tracks** only (the read-only view
   permission comes with it). Not the production permission: the human promotion step is also the
   security boundary.
3. **GitHub** — repository secret `PLAY_SERVICE_ACCOUNT_JSON` holding the key file's full contents.
   Delete the local copy. Set it **before** the next tag; without it the job fails on auth (the
   GitHub release is unaffected).
4. Internal and closed testing need tester lists containing the owner's account so the build is
   installable from Play; open testing is public, so every tag — RC included — is visible to anyone
   who opted in.

The job is the check for a new or rotated key: it is last, takes a minute, and *re-run failed jobs*
repeats only it, so a wrong key costs a re-run, not a release. A freshly invited service account can
get `403` for up to ~24 h; re-run until it clears. The current key, API enablement, invite and
testing-track write permission were proven on 2026-10-06 with a throwaway script that opened an
edit, wrote the `internal` track back unchanged and discarded the edit (it lived on the #289 branch
as `scripts/play-api-probe.sh`; the repo squash-merges, so look there, not in `master`'s history).

### Verifying a pipeline change before it merges

Push-event workflows run **the workflow file at the pushed commit**, and `release.yml` triggers on
any `v*` tag, so an RC tag on the PR branch exercises that branch's pipeline end to end — real
signing, `upgrade_test`, the GitHub prerelease and the Play upload — with nothing merged:

```bash
git push                                  # the branch first, so the PR's CI sees the same commit
git tag v2.2.5.0-rcN && git push origin v2.2.5.0-rcN
gh run list --workflow=release.yml --limit 1 && gh run watch <run-id>
```

The tag stays valid after a squash merge; it just points off `master`'s first-parent line, which
nothing reads (N-1 selection only looks at stable tags). Costs one version code and a full run.

### Release notes

`distribution/whatsnew/<MAJOR.MINOR.DBVERSION.FIX>/whatsnew-<locale>`, chosen by the tag's base
version (`v2.2.5.0-rc3` → `2.2.5.0`), so an RC and its stable share one set and a previous
version's notes can never be reused. Plain text, **≤ 500 characters per locale counting the final
newline**, no markup, locale as Play lists it (`en-US`, `hi-IN`, `es-419`, `fil`). The default
listing language must be present for the notes to show.

- **No directory** is fine: the release is uploaded without notes (`::warning::` in the run) and
  they are typed in Play Console when promoting.
- **A directory that exists must be valid.** `scripts/lib/release-notes.sh` rejects anything other
  than non-empty `whatsnew-<locale>` files within the limit, because the action sends the name's
  suffix as the language verbatim and ignores other names (verified in its `src/whatsnew.ts`,
  v1.1.5), and Play enforces the limit only after the bundle upload.
  `scripts/tests/release-notes-test.sh` validates every committed directory on each PR.
- Write them in the PR that prepares the release, or in their own PR; `git log <N-1>..HEAD
  --first-parent` lists what changed.

### Promoting

Play Console → *Testing → Closed testing* → the release → **Promote release** → *Production*. The
bundle and notes come across pre-filled and editable; choose the rollout percentage there and send
it for review. A later tag supersedes the previous release on all three testing tracks by itself.

If a promotion is still in review when the next tag lands, Play withdraws that submission and
re-sends it together with the new testing-track changes (`edits.commit` default
`changesInReviewBehavior = CANCEL_IN_REVIEW_AND_SUBMIT`); the review restarts, nothing is lost.

`inAppUpdatePriority` is not set (default 0) and the app does not read it ([ADR-0008](../../../docs/decisions/0008-in-app-updates-flexible-only.md)). If it is ever needed it goes on the upload step; Play applies it per version code regardless of track and it cannot be changed after the first release of that code.

### When the job fails

| Symptom | Cause | Do |
|---|---|---|
| auth error on the upload step | secret missing or key revoked | set `PLAY_SERVICE_ACCOUNT_JSON`, re-run failed jobs |
| `403` right after setup | Play has not propagated the invite | wait up to a day, re-run failed jobs |
| "version code … has already been used" | re-run after a successful upload | nothing; the release is already on all three tracks |
| "Changes cannot be sent for review automatically. Please set the query parameter changesNotSentForReview to true" | the console holds un-sent or rejected changes (a saved listing edit, a rejected submission) | send or discard them in the console and re-run. Setting `changesNotSentForReview: true` on the step is a one-off escape, not a mode: once the queue is clear Play rejects the flag with "Changes are sent for review automatically" (ADR-0009) |
| the notes step fails | a committed `distribution/whatsnew/<version>/` is invalid — only possible past the PR suite, i.e. a direct push | a re-run checks out the same tag, so upload that `app-release.aab` by hand this once and fix the directory for the next tag |

The job is last, so any of these leaves the GitHub release intact; nothing needs deleting.

## Store listing

Listing text and screenshots are still manual. The screenshots and the README grid are generated,
not drawn — refresh them with the [store-screenshots skill](../store-screenshots/SKILL.md) after a
UI change, then upload `screenshots/readme/01-…08-….png` in order.
