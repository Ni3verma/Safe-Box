# Upgrade-test seed

The backup the upgrade test restores into N-1 when this commit's release is N-1, and the
current-format file the restore test restores into N. Replace it only with
`scripts/update-seed.sh`; the procedure is in
[upgrade-harness-operations.md](../../docs/testing/upgrade-harness-operations.md#capturing-a-seed).

- Captured from: debug build of envelope MR 8 (feat/envelope-mr8 on 7a46dac): the format-3 seed restored, then backed up
- Captured on: 2026-11-10
- Backup password: `Upgrade@@Test123`

To see what it holds: `java scripts/InspectBackup.java upgrade-test/seed/seed.bak Upgrade@@Test123`.
