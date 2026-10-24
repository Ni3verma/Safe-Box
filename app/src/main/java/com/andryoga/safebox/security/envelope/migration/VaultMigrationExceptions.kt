package com.andryoga.safebox.security.envelope.migration

/**
 * Non-PII exception recorded to Crashlytics when [LegacyVaultMigrator] fails.
 *
 * Its message carries only the stage, reason, table, and record counters; its cause preserves the
 * underlying Keystore or SQLite stack trace.
 */
open class VaultMigrationException(
    val stage: MigrationStage,
    val reason: MigrationFailureReason,
    val table: String = LegacyVaultMigrator.TABLE_NONE,
    val recordsDone: Int = 0,
    val totalRecords: Int = 0,
    message: String = "stage=${stage.wireName}, reason=${reason.wireName}, table=$table, done=$recordsDone/$totalRecords",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when a verify-after-encrypt, per-table row count, or password-wrap round-trip check fails.
 */
internal class VerifyMismatchException(
    stage: MigrationStage,
    detail: String,
    cause: Throwable? = null,
) : VaultMigrationException(
    stage = stage,
    reason = MigrationFailureReason.VERIFY_MISMATCH,
    message = detail,
    cause = cause,
)

/**
 * Thrown when a single stored field has a permanently corrupted GCM authentication tag or malformed
 * ciphertext structure so the enclosing row can be dropped during migration.
 */
internal class CorruptedRowException(
    cause: Throwable,
) : VaultMigrationException(
    stage = MigrationStage.DECRYPT_ROW,
    reason = MigrationFailureReason.UNEXPECTED,
    message = "Corrupted legacy ciphertext field",
    cause = cause,
)
