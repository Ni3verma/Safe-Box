package com.andryoga.safebox.security.envelope.migration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress snapshot of an in-flight legacy-to-envelope vault migration.
 */
sealed interface MigrationProgressState {
    /** No migration is currently running. */
    data object Idle : MigrationProgressState

    /**
     * A migration is running; [done] counts records in the current [phase] only.
     *
     * @property done Number of records handled in [phase] so far (`0..total`).
     * @property total Total number of records across all five record tables.
     * @property phase Which phase of the migration is running.
     */
    data class Running(
        val done: Int,
        val total: Int,
        val phase: MigrationPhase = MigrationPhase.DECRYPTING,
    ) : MigrationProgressState {
        /**
         * Fraction for the progress bar. Reading dominates (a Keystore round-trip per field), saving
         * is in-process AES, so reading spans [0, [DECRYPT_SHARE]] and saving the remainder.
         */
        val fraction: Float
            get() {
                val phaseFraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 1f
                return when (phase) {
                    MigrationPhase.PREPARING -> 0f
                    MigrationPhase.DECRYPTING -> DECRYPT_SHARE * phaseFraction
                    MigrationPhase.SAVING -> DECRYPT_SHARE + (1f - DECRYPT_SHARE) * phaseFraction
                }
            }

        companion object {
            /** Share of the bar given to the legacy decrypt pass. */
            const val DECRYPT_SHARE = 0.9f
        }
    }
}

/** The phases of a migration, in order. */
enum class MigrationPhase {
    /** Key setup before any record is read; Keystore key generation has no measurable progress. */
    PREPARING,

    /** Every record decrypted under the legacy Keystore key, outside the transaction. */
    DECRYPTING,

    /** Every record re-encrypted, verified and written inside the transaction. */
    SAVING,
}

/**
 * Application-scoped progress holder for [LegacyVaultMigrator].
 *
 * Lives in [Singleton] scope so a configuration change on the login screen re-subscribes to the
 * in-flight record counter without interrupting or restarting the migration transaction.
 */
@Singleton
class VaultMigrationProgress @Inject constructor() {

    private val _state = MutableStateFlow<MigrationProgressState>(MigrationProgressState.Idle)

    /** Current progress state observed by the login screen. */
    val state: StateFlow<MigrationProgressState> = _state.asStateFlow()

    /** Marks the start of a migration over [total] records, in the preparing phase. */
    fun start(total: Int) {
        _state.value = MigrationProgressState.Running(done = 0, total = total, phase = MigrationPhase.PREPARING)
    }

    /** Updates the counter to [done] of [total] records in [phase]. */
    fun update(phase: MigrationPhase, done: Int, total: Int) {
        _state.value = MigrationProgressState.Running(done = done, total = total, phase = phase)
    }

    /** Resets the progress state to [MigrationProgressState.Idle] once migration finishes. */
    fun reset() {
        _state.value = MigrationProgressState.Idle
    }
}
