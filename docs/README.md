# Safe-Box documentation

Long-form knowledge about the project. Short, always-relevant facts live in
[`.agents/PROJECT_FACTS.md`](../.agents/PROJECT_FACTS.md); step-by-step procedures live in
[`.agents/skills/`](../.agents/skills).

## Architecture

- [Persistence and cryptography](architecture/persistence-and-crypto.md) — the six storage layers,
  the `symmetricDataKey` Keystore failure mode, the backup file format and its version history,
  restore semantics.
- [TOTP auth record type design](TotpAuthRecordTypeDesign.md)
- [In-app updates](architecture/in-app-updates.md): Play flexible updates, the lock-aware restart,
  analytics bounds, and how to test with real Play.
- [Localization](architecture/localization.md) — 26-locale strings workflow, `app/translations.lock`
  stale-translation detection, local `preBuild` auto-sync vs strict CI enforcement.

## Design

- [Envelope encryption and session key architecture](design/envelope-encryption.md) — the staged
  plan to replace the ambient Keystore key with a data key wrapped by the master password, a
  Class-3 biometric and the device credential: key hierarchy, Room schema v6, backup format v4,
  every user flow, the MR-by-MR plan, the spike checklist and the manual test plan. Measured
  spike results: [ADR-0011](decisions/0011-envelope-encryption-spike-results.md).

## Testing

- [Testing strategy](testing/testing-strategy.md) — what is tested where, and the deliberate gaps.
- [Upgrade and restore testing](testing/upgrade-testing.md) — what the black-box QA tests prove and
  how: decoded backup comparison, the seed, the release gate.
- [Upgrade harness operations](testing/upgrade-harness-operations.md) — running the tests, capturing
  a seed, selector traps, triage.

## Store listing

- [Store listing images](store-listing.md) — how the Play screenshots and the README grid are
  generated from the app, the scene set and why, the `scenes.json` / `theme.json` formats, and the
  presentation issues the images exposed.

## UI

- [Motion](ui/motion.md) — the animation vocabulary (fade through, shared axis, reject shake,
  glyph swap, reveal, reflow), the shared tokens behind it, where each pattern is used, how it is
  tested, and how to capture animations on the emulator for review.

## Analytics

- [Post-signup activation funnel](analytics/activation-funnel.md) — why every funnel step is an
  explicit event in this single-Activity app, what each event means and is bounded by, what was
  deliberately not logged, and how to read the Firebase funnel into a decision.
- [Firebase, GA and BigQuery setup](analytics/firebase-setup.md) — project and property
  identifiers, the BigQuery export and its sandbox limits, which parameters are registered as
  custom definitions, where a parameter's value can be seen, and query recipes.
- [Envelope-encryption readiness telemetry](analytics/envelope-readiness.md) — the logging-only
  events shipped ahead of the envelope-encryption work, the decision each one feeds, the read-back
  deadline the BigQuery sandbox imposes, and one query per question.

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
[the pre-commit hook](../CICD/gitHooks/pre-commit.sh), and is therefore not set in your shell. Load
it from there rather than copying the regex around — it is deliberately not reproduced in any
markdown file, because the pattern matches its own text and the check would then fail on the file
documenting it:

```bash
DOCS_POLICY_PATTERN=$(sed -n "s/^DOCS_POLICY_PATTERN='\(.*\)'$/\1/p" CICD/gitHooks/pre-commit.sh)
git grep -nE "$DOCS_POLICY_PATTERN" -- '*.md'
```

The policy check also runs in the pre-commit hook, restricted to the markdown you have staged. See
[the build-and-test skill](../.agents/skills/build-and-test/SKILL.md) for how to get `lychee`, which
is not installed by default.
