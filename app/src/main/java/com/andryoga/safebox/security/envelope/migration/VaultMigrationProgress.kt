package com.andryoga.safebox.security.envelope.migration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a legacy-to-envelope vault migration is in flight. There is no measurable progress: the
 * largest production vault migrates its records in about 1.6 s (ADR-0015).
 */
sealed interface MigrationProgressState {
    /** No migration is currently running. */
    data object Idle : MigrationProgressState

    /** A migration is running. */
    data object Running : MigrationProgressState
}

/**
 * Application-scoped running flag for [LegacyVaultMigrator].
 *
 * Lives in [Singleton] scope so a configuration change on the login screen re-subscribes to the
 * in-flight migration without interrupting or restarting its transaction.
 */
@Singleton
class VaultMigrationProgress @Inject constructor() {

    private val _state = MutableStateFlow<MigrationProgressState>(MigrationProgressState.Idle)

    /** Current state observed by the login screen. */
    val state: StateFlow<MigrationProgressState> = _state.asStateFlow()

    /** Marks the start of a migration. */
    fun start() {
        _state.value = MigrationProgressState.Running
    }

    /** Resets the state to [MigrationProgressState.Idle] once the migration finishes. */
    fun reset() {
        _state.value = MigrationProgressState.Idle
    }
}
