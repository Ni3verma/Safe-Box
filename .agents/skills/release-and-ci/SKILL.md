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

- Local builds get `9999999`, so a local APK always installs over any CI build.
- `versionName` is derived from `GITHUB_REF_NAME`: `v2.0.4.0` → `2.0.4.0`; a branch → `<branch>-build.<code>`; nothing → `LOCAL-build`.
- Tag format is **`vMAJOR.MINOR.DBVERSION.FIX`**. The third component tracks the **Room schema
  version**, so bumping the DB requires bumping it in the next tag.

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

`release_on_github` attaches everything it downloaded, so every tag carries:

- `SafeBox-qa.apk` — minified, release-like, stably signed
- `app-release.aab` (since `v2.1.4.0-rc1`)

This is a **permanent, addressable archive of every shipped QA build**. Of 18 releases, 15 carry a
QA APK; the three that do not (`v1.0.0`, `v1.1.0`, `v1.2.2.0`) predate the upload step, so the
oldest usable baseline is `v1.3.3.0`.

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

> [!IMPORTANT]
> `gh` requires a token even for **read-only** access to a public repository. If it prints
> `To get started with GitHub CLI, please run: gh auth login`, it is unauthenticated — that is a
> one-time step only the repository owner can perform. **Never attempt to authenticate on the
> user's behalf or handle their token.**

### Fallback when `gh` is unauthenticated

The repository is public, so the unauthenticated REST API still works for reads and needs no
credentials at all:

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
