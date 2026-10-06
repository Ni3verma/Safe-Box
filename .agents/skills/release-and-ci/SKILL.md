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
                                               └─ release_on_play → app-release.aab to Play's closed + open testing tracks
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

`gh` **is installed** — `/usr/local/bin/gh`, version 2.100.0. Prefer it over hand-rolled REST
calls. Verified 2026-09-21.

```bash
gh release list --limit 10                                   # tags and their assets
gh release download v2.0.4.0 -p 'SafeBox-qa.apk' -D old/     # fetch a baseline APK
gh pr view 241 --comments                                    # PR discussion, incl. bot review
gh pr checks 241                                             # CI status for a PR
gh run list --workflow=release.yml --limit 5                 # recent release runs
gh run watch <run-id>                                        # follow a run to completion
gh api repos/Ni3verma/Safe-Box/pulls/241/comments            # raw API when a subcommand is missing
```

### A new `workflow_dispatch` workflow cannot be run before it merges

GitHub resolves workflows from the **default branch**. A workflow file that exists only on a feature
branch has no "Run workflow" button, is absent from `gh workflow list`, and `gh workflow run --ref
<branch>` cannot find it. Verified 2026-09-22 with `gh workflow list --all`.

The way out is a trigger that resolves from the PR: `pull_request` runs the workflow file from the
PR's **merge commit** (`refs/pull/N/merge`). So what runs is the merged result, and **a PR with a
merge conflict fires no `pull_request` run at all**. `upgrade-test.yml` gates it on the
`run-upgrade-test` label so ordinary pushes cost nothing; remove and re-add the label to run again.

### Current authentication state

Verified 2026-09-21. `gh` **is** authenticated on this machine with a fine-grained token scoped to
this repository. `gh auth status` shows **no** `Token scopes:` line — that absence is how you tell a
fine-grained token from a classic OAuth grant. If you ever see `Token scopes: 'gist', 'read:org',
'repo'`, that is gh's default browser OAuth flow and it carries **full write access**; flag it.

Permissions as granted in the GitHub UI. **The grant is the authority; probes only corroborate it.**
A status code cannot prove a permission on its own — GitHub does not order existence and permission
checks consistently, and a `403` can come from repository rules rather than the token.

| Permission | Granted | Corroborating probe |
|---|---|---|
| Reads (PRs, releases, runs, checks) | read | every read command works |
| `Contents` | read | `PUT` via the contents API returned `403` |
| `Issues` | **read + write**, granted deliberately so the agent can file issues | `gh issue create` (#282) and `gh pr edit --body-file` (#281) both succeeded 2026-10-03, but **labels on a pull request are refused**, see below |

> [!IMPORTANT]
> **Labels cannot be changed from here.** `DELETE /repos/Ni3verma/Safe-Box/issues/259/labels/run-upgrade-test`
> returned `403 Resource not accessible by personal access token` (2026-09-22). The label existed
> and was applied, so it is a permission result, not a 404 in disguise. Re-running the upgrade test
> on a PR (remove and re-add the label) has to be done by a human in the UI. Ask; do not retry.

> [!IMPORTANT]
> Merging a PR needs `Contents: write`, which is provably `403`, so **a merge cannot succeed from
> here**. Editing a PR's title/body **does** work — `gh pr edit 281 --body-file …` succeeded on
> 2026-10-03 — while labels on the same PR are refused (see above). Treat the permission list in
> the GitHub UI as authoritative, not a probe's status code.

> [!IMPORTANT]
> **`git push` does not work from the agent and is not covered by the token.** It uses a completely
> separate credential, and this machine has no HTTPS credential helper configured: `git push` hangs
> on `Username for 'https://github.com':` and must be cancelled. Commit locally, then **ask the user
> to push**. Do not try to supply credentials. Observed 2026-09-21 pushing `docs/agent-knowledge-base`.

> [!IMPORTANT]
> **Do not silently fall back to unauthenticated access.** If `gh` ever prints
> `To get started with GitHub CLI, please run: gh auth login`, say so explicitly, state the cost, and
> ask — re-authenticating is a one-time step only the repository owner can perform, and you must
> never handle their token. Measured 2026-09-21: `gh pr view 241 --comments` is **one** tool call;
> the unauthenticated equivalent took **eight** (four `curl` calls plus four hand-written JSON
> parsers) for the same answer, plus raw JSON through context. Mentioning it in passing while
> proceeding anyway is not flagging it — it reads as "handled" and leaves the tax in place.

### Fallback when `gh` is unauthenticated

Historical; `gh` is authenticated as of 2026-09-21, so this should not be needed. Use it only after
flagging the above. The repository is public, so the unauthenticated REST API still works for reads
and needs no credentials at all:

```bash
curl -s "https://api.github.com/repos/Ni3verma/Safe-Box/releases?per_page=10"
curl -sL -o old.apk \
  "https://github.com/Ni3verma/Safe-Box/releases/download/<tag>/SafeBox-qa.apk"
```

PR review comments are at `/repos/Ni3verma/Safe-Box/pulls/<n>/comments`.

Note that `gh` and `curl` both need network access, so they must run **outside the sandbox**.

## Crashlytics

`mappingFileUploadEnabled` is deliberately **false** for `qa`, because it is an indirect input to
the minify task and would defeat incremental builds. If a qa stack trace needs deobfuscating,
upload `app/build/outputs/mapping/qa/mapping.txt` manually.

## Play upload

Decision and alternatives: [ADR-0009](../../../docs/decisions/0009-play-upload-testing-tracks.md).
`release_on_play` uploads the `app-release.aab` artifact — the same file the GitHub release carries —
to the **closed testing (`alpha`) and open testing (`beta`)** tracks as completed releases, in one
edit, on every `v*` tag, RC and stable. Committing the edit sends both for review by itself; the
API cannot hold them for a manual *Send for review* (the flag for that is only accepted when Play
already has un-sent changes queued, see the ADR). Nothing here reaches production.

### One-time setup (owner only; the agent cannot do any of it)

1. **GCP** — in the Firebase project's GCP console (or any project): enable the *Google Play Android
   Developer API*; create a service account with no GCP roles; create a JSON key for it.
2. **Play Console** — *Users and permissions → Invite new users*, the service account's email,
   app permission on Safe-Box: **Release apps to testing tracks** only (the read-only view
   permission comes with it). Not the production permission: the human promotion step is also the
   security boundary.
3. **Probe the key before storing it** — `./scripts/play-api-probe.sh <key.json>` (your shell, not
   the sandbox: it needs network). It mints the same OAuth token the action does, opens an edit,
   lists the tracks, writes `alpha` and `beta` back unchanged and discards the edit, so nothing
   changes in Play. A failure names which setup mistake it is: API not enabled, invite not
   propagated, wrong package, or the permission missing on a track. Passed on 2026-10-06 for
   `internal`; the `alpha`/`beta` variant has not been run against the real key yet (the local copy
   was deleted after the secret was set), so its first real run is the next tag.
4. **GitHub** — repository secret `PLAY_SERVICE_ACCOUNT_JSON` holding the key file's full contents.
   Delete the local copy. Set it **before** the next tag; without it the job fails on auth (the
   GitHub release is unaffected).
5. Closed testing needs a tester list containing the owner's account so the build is installable
   from Play; open testing is public, so every tag — RC included — is visible to anyone who opted in.

A freshly invited service account can get `403` for up to ~24 h; the probe shows when it clears.

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
it for review. A later tag supersedes the previous release on both testing tracks by itself.

If a promotion is still in review when the next tag lands, Play withdraws that submission and
re-sends it together with the new testing-track changes (`edits.commit` default
`changesInReviewBehavior = CANCEL_IN_REVIEW_AND_SUBMIT`); the review restarts, nothing is lost.

`inAppUpdatePriority` is not set (default 0) and the app does not read it ([ADR-0008](../../../docs/decisions/0008-in-app-updates-flexible-only.md)). If it is ever needed it goes on the upload step; Play applies it per version code regardless of track and it cannot be changed after the first release of that code.

### When the job fails

| Symptom | Cause | Do |
|---|---|---|
| auth error on the upload step | secret missing or key revoked | set `PLAY_SERVICE_ACCOUNT_JSON`, re-run failed jobs |
| `403` right after setup | Play has not propagated the invite | wait up to a day, re-run failed jobs |
| "version code … has already been used" | re-run after a successful upload | nothing; the release is already on both tracks |
| "Changes cannot be sent for review automatically. Please set the query parameter changesNotSentForReview to true" | the console holds un-sent or rejected changes (a saved listing edit, a rejected submission) | send or discard them in the console and re-run. Setting `changesNotSentForReview: true` on the step is a one-off escape, not a mode: once the queue is clear Play rejects the flag with "Changes are sent for review automatically" (ADR-0009) |
| the notes step fails | a committed `distribution/whatsnew/<version>/` is invalid — only possible past the PR suite, i.e. a direct push | a re-run checks out the same tag, so upload that `app-release.aab` by hand this once and fix the directory for the next tag |

The job is last, so any of these leaves the GitHub release intact; nothing needs deleting.

## Store listing

Listing text and screenshots are still manual. The screenshots and the README grid are generated,
not drawn — refresh them with the [store-screenshots skill](../store-screenshots/SKILL.md) after a
UI change, then upload `screenshots/readme/01-…08-….png` in order.
