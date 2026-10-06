---
name: github-tooling
description: How the agent reaches GitHub from this machine - gh is installed and authenticated with a fine-grained token, what that token can and cannot do (no push, no merge, no labels), the unauthenticated fallback, and why a new workflow_dispatch workflow cannot be run before it merges
---

# GitHub tooling

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

## A new `workflow_dispatch` workflow cannot be run before it merges

GitHub resolves workflows from the **default branch**. A workflow file that exists only on a feature
branch has no "Run workflow" button, is absent from `gh workflow list`, and `gh workflow run --ref
<branch>` cannot find it. Verified 2026-09-22 with `gh workflow list --all`.

The way out is a trigger that resolves from the PR: `pull_request` runs the workflow file from the
PR's **merge commit** (`refs/pull/N/merge`). So what runs is the merged result, and **a PR with a
merge conflict fires no `pull_request` run at all**. `upgrade-test.yml` gates it on the
`run-upgrade-test` label so ordinary pushes cost nothing; remove and re-add the label to run again.

## Current authentication state

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

## Fallback when `gh` is unauthenticated

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
