# Upgrade-test seed

The backup the upgrade test restores into N-1 when this commit's release is N-1, and the
current-format file the restore test restores into N. Replace it only with
`scripts/update-seed.sh`; the procedure is in
[upgrade-harness-operations.md](../../docs/testing/upgrade-harness-operations.md#capturing-a-seed).

- Captured from: debug build (LOCAL-build) of feature/upgrade-restore-test, based on master e6adee2
- Captured on: 2026-09-26
- Backup password: `Upgrade@@Test123`

To see what it holds: `java scripts/InspectBackup.java upgrade-test/seed/seed.bak Upgrade@@Test123`.
