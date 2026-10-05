# Architecture Decision Records

Short records of decisions that were **expensive to reach** and would otherwise be re-litigated.

An ADR belongs here when someone — human or agent — is likely to look at the current state of the
code, think "surely we should just do X", and burn hours rediscovering why X does not work.

| # | Decision | Status |
|---|---|---|
| [0001](0001-instrumentation-tests-run-on-debug-only.md) | Instrumentation tests run on the `debug` build only | Accepted |
| [0002](0002-upgrade-testing-via-black-box-uiautomator.md) | Upgrade and release-build testing via a black-box UI Automator module | Accepted, amended by 0003, 0004 |
| [0003](0003-ui-labels-from-resource-names.md) | UI labels are resolved from the app's own resource names | Accepted, amended by 0004 |
| [0004](0004-verify-upgrade-and-restore-by-decoded-backup.md) | Verify upgrade and restore by decoded backup comparison | Accepted |
| [0005](0005-qr-decoding-with-zxing-core.md) | QR codes are decoded with ZXing `core`, not ML Kit | Accepted |
| [0006](0006-store-screenshots-from-emulator-captures.md) | Store screenshots are composited from emulator captures of the real app | Accepted |
| [0007](0007-screen-owned-top-app-bars.md) | Top app bars are owned by each screen's `Scaffold`; the home `NavHost` pins all six transitions | Accepted |
| [0008](0008-in-app-updates-flexible-only.md) | In-app updates use the FLEXIBLE flow only, with a lock-aware restart | Accepted |

## Format

Keep them short. Context, decision, why, consequences. Include the **evidence** — a command output,
a table, a stack trace — because an assertion without evidence gets re-litigated anyway.

Number them sequentially. Never delete one; supersede it and mark the old one `Superseded by NNNN`.
