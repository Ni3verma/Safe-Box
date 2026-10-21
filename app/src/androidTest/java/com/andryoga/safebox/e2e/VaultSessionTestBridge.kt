package com.andryoga.safebox.e2e

import androidx.test.core.app.ApplicationProvider
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Opens the vault session that the E2E seed helpers need.
 *
 * The seeds write through the repositories before the Activity is launched, and every record field
 * is encrypted under the session key, so a seed without a session fails closed with
 * `SessionLockedException`. This does exactly what the production bridges do — the signup
 * bridge's `getOrCreateLegacyVaultKey()` and the login bridge's `setLegacySession` — against the
 * same singletons the Activity will use, so the login that follows reads the seeded rows back.
 *
 * Resolved through an entry point rather than injected into every test class: eleven suites call
 * the seed helpers and none of them should have to know about the session to seed a vault.
 */
// TODO(MR5): seeds create crypto_version = 2 vaults through VaultRepository and the session becomes a DEK (setSessionKey)
object VaultSessionTestBridge {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VaultSessionEntryPoint {
        fun sessionKeyHolder(): SessionKeyHolder
        fun keystoreGateway(): KeystoreGateway
    }

    private val entryPoint: VaultSessionEntryPoint
        get() = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            VaultSessionEntryPoint::class.java,
        )

    /**
     * Creates the legacy vault key if the fake Keystore has none yet and opens a legacy session on
     * it, so repository writes and reads work before and after the UI login.
     */
    fun openLegacySessionForSeeding() {
        val entryPoint = entryPoint
        val legacyKey = entryPoint.keystoreGateway().getOrCreateLegacyVaultKey()
        entryPoint.sessionKeyHolder().setLegacySession(legacyKey)
    }

    /** Locks the vault, as the session lifecycle will once MR 4 wires `clearSessionKey()`. */
    fun lockVault() {
        entryPoint.sessionKeyHolder().clearSessionKey()
    }
}
