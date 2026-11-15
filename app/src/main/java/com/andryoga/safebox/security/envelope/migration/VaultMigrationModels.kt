package com.andryoga.safebox.security.envelope.migration

import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity

/**
 * Trigger that initiated the legacy-to-envelope vault migration.
 *
 * @property wireName Analytics wire value for `AnalyticsParam.TRIGGER`.
 */
enum class MigrationTrigger(val wireName: String) {
    /** Migration initiated from normal password login. */
    LOGIN("login"),

    /** Migration initiated from the forgot-password reset flow. */
    FORGOT_PASSWORD("forgot_password"),
}

/**
 * Discrete execution stage of [LegacyVaultMigrator], reported to analytics and Crashlytics.
 *
 * @property wireName Analytics and Crashlytics wire value.
 */
enum class MigrationStage(val wireName: String) {
    /** Loading the legacy `symmetricDataKey` from Android Keystore. */
    LOAD_LEGACY_KEY("load_legacy_key"),

    /** Minting the 32-byte DEK, deriving PBKDF2 parameters, and wrapping the DEK. */
    DERIVE_KDF("derive_kdf"),

    /** Decrypting a legacy record field with the Keystore key. */
    DECRYPT_ROW("decrypt_row"),

    /** Decrypting the legacy password hint. */
    DECRYPT_HINT("decrypt_hint"),

    /** Encrypting a record field with the newly minted DEK. */
    ENCRYPT_ROW("encrypt_row"),

    /** Decrypting a freshly encrypted field back to verify it matches the plaintext. */
    VERIFY_ROW("verify_row"),

    /** Verifying per-table row counts after re-encryption. */
    VERIFY_COUNT("verify_count"),

    /** Verifying that unwrapping `wrapped_dek_password` yields the minted DEK. */
    VERIFY_WRAP("verify_wrap"),

    /** Committing the `vault_meta` keys inside the migration transaction. */
    COMMIT_TX("commit_tx"),
}

/**
 * Structured failure reason for [LegacyVaultMigrator].
 *
 * @property wireName Analytics wire value for `AnalyticsParam.REASON`.
 */
enum class MigrationFailureReason(val wireName: String) {
    /** The legacy `symmetricDataKey` entry is missing from Android Keystore. */
    LEGACY_KEY_LOST("legacy_key_lost"),

    /** Transient Android Keystore error that persisted after bounded retries. */
    KEYSTORE_TRANSIENT("keystore_transient"),

    /** Verify-after-encrypt, row-count, or password-wrap round-trip check failed. */
    VERIFY_MISMATCH("verify_mismatch"),

    /** SQLite or filesystem I/O failure while committing the migration transaction. */
    DB_IO("db_io"),

    /** Unforeseen runtime exception during migration. */
    UNEXPECTED("unexpected"),
}

/**
 * Result of running [LegacyVaultMigrator.migrate].
 */
sealed interface MigrationOutcome {
    /**
     * Migration succeeded and the session key is now open with [dek].
     *
     * @property dek Newly minted 32-byte DEK.
     */
    data class Success(
        val dek: ByteArray,
    ) : MigrationOutcome

    /** The vault was already at `crypto_version = 2` before this call acquired the migration lock. */
    data object AlreadyMigrated : MigrationOutcome

    /**
     * Migration failed and any database changes were rolled back, leaving `crypto_version = 1` intact.
     *
     * @property stage Stage at which the failure occurred.
     * @property reason Categorized failure reason.
     * @property attempt 1-based migration attempt ordinal within the current process.
     * @property trigger Whether the migration was started from login or forgot-password.
     * @property cause Underlying exception, if any.
     */
    data class Failure(
        val stage: MigrationStage,
        val reason: MigrationFailureReason,
        val attempt: Int = 1,
        val trigger: MigrationTrigger = MigrationTrigger.LOGIN,
        val cause: Throwable? = null,
    ) : MigrationOutcome
}

internal data class DecryptedTableResult<T>(
    val rows: List<T>,
    val corruptedKeys: List<Int>,
)

internal data class DecryptedLogin(
    val entity: LoginDataEntity,
    val url: String?,
    val password: String?,
    val notes: String?,
    val userId: String,
)

internal data class DecryptedBankAccount(
    val entity: BankAccountDataEntity,
    val accountNumber: String,
    val customerId: String?,
    val ifscCode: String?,
    val micrCode: String?,
    val notes: String?,
)

internal data class DecryptedBankCard(
    val entity: BankCardDataEntity,
    val name: String?,
    val number: String,
    val pin: String?,
    val cvv: String?,
    val expiryDate: String?,
    val notes: String?,
)

internal data class DecryptedSecureNote(
    val entity: SecureNoteDataEntity,
    val notes: String,
)

internal data class DecryptedAuthenticator(
    val entity: AuthenticatorDataEntity,
    val secretKey: String,
)
