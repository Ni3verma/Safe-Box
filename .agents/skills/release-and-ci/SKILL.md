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
| `run-ui-test.yml` | manual / reusable | on-demand UI test run |
| `gemini-pr-review.yml` | PR | automated bot review |

## `release.yml` job graph

```
quality ──┬─ debug_pipeline    → SafeBox-debug.apk (artifact, 7d)
          ├─ release_pipeline  → app-release.aab + Crashlytics symbols
          └─ qa_pipeline       → SafeBox-qa.apk + mapping.txt
                    │
                    └─ release_on_github → GitHub Release with both binaries attached
```

`quality` runs lint, unit tests, `pixel8Api34DebugAndroidTest`, and an **NDK gatekeeper** that
fails the build if the runner lacks the exact `ndkVersion` from `app/build.gradle`.

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
> twenties. Anything that installs a freshly built APK over a released one has to override it.
>
> **`env:` cannot do it.** It is a default variable, and the docs say "if you attempt to override
> the value of one of these default variables, the assignment is ignored" — silently. Observed
> twice on 2026-09-22: run 1 of `upgrade-test.yml` failed with `the build under test (16) does not
> supersede the baseline (23)`, and after adding `env: GITHUB_RUN_NUMBER: 9999984` run 2 failed the
> same way with 17, the value simply tracking the run number. Assign it on the command instead,
> where it is an ordinary child-process variable:
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

Neither keystore is committed. `google-services.json` is also GPG-encrypted in CI.

The QA certificate has been **stable since at least `v2.0.4.0`**:
SHA-256 `257ab2043588f0b355bba6a9c9f199c088f079f6306536cd4c94fc2eba7b113d`.
That stability is what makes APK-over-APK upgrade testing possible.

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
upload step, so the oldest usable baseline is `v1.3.3.0`. Resolve a baseline against that floor
rather than assuming any tag will do.

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
<branch>` cannot find it — the docs say the dispatch trigger requires the workflow to be on the
default branch, and dispatching against another ref only works *after* it has run at least once.
Verified 2026-09-22: `.github/workflows/upgrade-test.yml` existed on `upgrade-test/mr1-harness-skeleton`
and `gh workflow list --all` did not list it.

This is a chicken-and-egg problem for any PR whose entire point is a new job: it cannot be proved
until it is merged unproved. The way out is a trigger that resolves from the PR rather than from
the default branch: `pull_request` runs the workflow file taken from the PR's **merge commit**
(`GITHUB_REF` is `refs/pull/N/merge`), which for a same-repo PR is the branch's version of the file
merged into the base. Two consequences follow: what runs is the merged result rather than the head
branch alone, and **a PR with a merge conflict fires no `pull_request` run at all** — the conflict
has to be resolved before the job can be triggered. Gate it on a label so ordinary pushes cost
nothing:

```yaml
on:
  workflow_dispatch:
  pull_request:
    types: [ labeled ]
jobs:
  job:
    if: github.event_name == 'workflow_dispatch' || github.event.label.name == 'run-upgrade-test'
```

Remove and re-add the label to run it again. Note that `inputs.*` is empty on the label path, so
give every input a fallback (`${{ inputs.x || 'default' }}`).

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
| `Issues` | **read + write**, granted deliberately so the agent can file issues | writing to an *issue* is untested — creating one just to check is not worth it — but **labels on a pull request are refused**, see below |

> [!IMPORTANT]
> **Labels cannot be changed from here.** `DELETE /repos/Ni3verma/Safe-Box/issues/259/labels/run-upgrade-test`
> returns `403 Resource not accessible by personal access token` (2026-09-22). This is a conclusive
> negative: the label existed and was applied, so it is a permission result, not a 404 in disguise.
> Anything driven by a label — including re-running the upgrade test, which needs the label removed
> and re-added — has to be done by a human in the UI. Ask; do not retry.

> [!IMPORTANT]
> Merging a PR needs `Contents: write`, which is provably `403`, so **a merge cannot succeed from
> here**. A `PATCH` probe against a non-existent PR returned `404` rather than `403`, so PR-write is
> formally unproven — GitHub does not order existence and permission checks consistently. Treat the
> permission list in the GitHub UI as authoritative, not a probe's status code.

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
