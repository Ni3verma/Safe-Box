package com.andryoga.safebox.security.interfaces

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.SecretKey

/**
 * Holds the active vault session key in RAM and broadcasts lock transitions.
 *
 * The holder is the single source of truth for whether the vault is open (`Unlocked` with a 32-byte
 * DEK or `LegacyUnlocked` with a non-exportable Keystore handle) or `Locked`.
 */
interface SessionKeyHolder {
    /** Observable so the UI leaves the vault the moment it is cleared, whoever clears it. */
    val isUnlocked: StateFlow<Boolean>
    // TODO(D3): legacy vault (crypto_version = 1) only — the fallback session and everything that serves it
    val isLegacySession: Boolean
    val lockEvents: SharedFlow<Unit>

    /**
     * Executes [block] with a short-lived [SecretKey] for the current session, or the legacy
     * Keystore handle when in a legacy fallback session.
     *
     * @param block Operation that consumes the active [SecretKey].
     * @return The value produced by [block].
     * @throws SessionLockedException When the vault is currently locked.
     */
    fun <T> withSessionKey(block: (SecretKey) -> T): T

    /**
     * Installs a defensive copy of [dekBytes] as the active session DEK.
     *
     * @param dekBytes Raw 32-byte AES-256 Data Encryption Key; the caller should zero its own array.
     */
    fun setSessionKey(dekBytes: ByteArray)

    /**
     * Opens a fallback session backed by the non-exportable legacy Keystore handle.
     *
     * @param legacyKey Keystore [SecretKey] handle for `symmetricDataKey`.
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    fun setLegacySession(legacyKey: SecretKey)

    /**
     * Zero-fills any held DEK buffer, drops the active session state, and emits on [lockEvents].
     */
    fun clearSessionKey()
}

/**
 * Thrown when a data-layer or cryptographic operation requires an open vault while locked.
 */
class SessionLockedException : SecurityException("Vault is locked")
