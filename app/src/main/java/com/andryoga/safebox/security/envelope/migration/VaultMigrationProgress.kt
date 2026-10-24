package com.andryoga.safebox.security.envelope.migration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determinate progress snapshot of an in-flight legacy-to-envelope vault migration.
 */
sealed interface MigrationProgressState {
    /** No migration is currently running. */
    data object Idle : MigrationProgressState

    /**
     * A migration is actively decrypting and re-encrypting records.
     *
     * @property done Number of records processed so far (`0..total`).
     * @property total Total number of records across all five record tables.
     */
    data class Running(
        val done: Int,
        val total: Int,
    ) : MigrationProgressState
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

    /** Current determinate progress state observed by the login screen. */
    val state: StateFlow<MigrationProgressState> = _state.asStateFlow()

    /** Marks the start of a migration over [total] records. */
    fun start(total: Int) {
        _state.value = MigrationProgressState.Running(done = 0, total = total)
    }

    /** Updates the determinate counter to [done] of [total] records. */
    fun onRecordProcessed(done: Int, total: Int) {
        _state.value = MigrationProgressState.Running(done = done, total = total)
    }

    /** Resets the progress state to [MigrationProgressState.Idle] once migration finishes. */
    fun reset() {
        _state.value = MigrationProgressState.Idle
    }
}
