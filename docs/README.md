# Safe-Box documentation

Long-form knowledge about the project. Short, always-relevant facts live in
[`.agents/PROJECT_FACTS.md`](../.agents/PROJECT_FACTS.md); step-by-step procedures live in
[`.agents/skills/`](../.agents/skills).

## Architecture

- [Persistence and cryptography](architecture/persistence-and-crypto.md) — the six storage layers,
  the `symmetricDataKey` Keystore failure mode, the backup file format and its version history,
  restore semantics.
- [TOTP auth record type design](TotpAuthRecordTypeDesign.md)

## Testing

- [Testing strategy](testing/testing-strategy.md) — what is tested where, and the deliberate gaps.
- [Upgrade testing](testing/upgrade-testing.md) — the APK-over-APK design.

## Decisions

- [Architecture Decision Records](decisions/README.md)

## Where things go

| Content | Home | Loaded |
|---|---|---|
| Behavioural rules for agents | `.agents/AGENTS.md` | always |
| Verified facts about the repo | `.agents/PROJECT_FACTS.md` | on request, always read first |
| Procedures, commands, gotchas | `.agents/skills/<name>/SKILL.md` | on demand |
| Explanations and designs | `docs/` | on demand |
| Decisions and their evidence | `docs/decisions/` | on demand |

## Validating this set

Two checks, both run in CI and neither needing Gradle:

| Check | Command |
|---|---|
| Broken relative links | `lychee --offline '.agents/**/*.md' 'docs/**/*.md' 'upgrade-test/**/*.md'` |
| Stray agent markup, Windows link targets | `git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'` |

`DOCS_POLICY_PATTERN` is defined once, in
[the pre-commit hook](../CICD/gitHooks/pre-commit.sh) — source that file to get it, rather than
copying the regex around. It is deliberately not reproduced here: the pattern matches its own text,
so pasting it into a markdown file makes the check fail on that file.

The policy check also runs in the pre-commit hook, restricted to the markdown you have staged. See
[the build-and-test skill](../.agents/skills/build-and-test/SKILL.md) for how to get `lychee`, which
is not installed by default.
