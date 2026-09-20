# Architecture Decision Records

Short records of decisions that were **expensive to reach** and would otherwise be re-litigated.

An ADR belongs here when someone — human or agent — is likely to look at the current state of the
code, think "surely we should just do X", and burn hours rediscovering why X does not work.

| # | Decision | Status |
|---|---|---|
| [0001](0001-instrumentation-tests-run-on-debug-only.md) | Instrumentation tests run on the `debug` build only | Accepted |
| [0002](0002-upgrade-testing-via-black-box-uiautomator.md) | Upgrade and release-build testing via a black-box UI Automator module | Accepted (design) |

## Format

Keep them short. Context, decision, why, consequences. Include the **evidence** — a command output,
a table, a stack trace — because an assertion without evidence gets re-litigated anyway.

Number them sequentially. Never delete one; supersede it and mark the old one `Superseded by NNNN`.
