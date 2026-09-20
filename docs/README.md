# Safe-Box documentation

Long-form knowledge about the project. Short, always-relevant facts live in
[`.agents/PROJECT_FACTS.md`](../.agents/PROJECT_FACTS.md); step-by-step procedures live in
[`.agents/skills/`](../.agents/skills).

## Architecture

- [Persistence and cryptography](architecture/persistence-and-crypto.md) — the five storage layers,
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

```bash
python3 scripts/check_docs.py
```

Fails on broken relative links and on stray agent-harness markup left in file tails.
