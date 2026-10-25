package com.andryoga.safebox.e2e

import androidx.test.core.app.ApplicationProvider
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Opens the vault session that the E2E seed helpers need.
 *
 * The seeds write through the repositories before the Activity is launched, and every record field
 * is encrypted under the session key, so a seed without a session fails closed with
 * `SessionLockedException`. [seedVaultAndOpenSession] creates a `crypto_version = 2` vault via
 * [VaultRepository.createVault] and opens a DEK session on the minted key; [openSessionForSeeding]
 * ensures a DEK session is open for suites that seed records without a user account or reopen the
 * session after an explicit [lockVault]; [seedBiometricWrap] adds the wrap the fingerprint login
 * needs.
 *
 * Resolved through an entry point rather than injected into every test class: eleven suites call
 * the seed helpers and none of them should have to know about the session to seed a vault.
 */
object VaultSessionTestBridge {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VaultSessionEntryPoint {
        fun sessionKeyHolder(): SessionKeyHolder
        fun vaultRepository(): VaultRepository
        fun envelopeEncryptionManager(): EnvelopeEncryptionManager
    }

    private val entryPoint: VaultSessionEntryPoint
        get() = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            VaultSessionEntryPoint::class.java,
        )

    @Volatile
    private var lastSeededDek: ByteArray? = null

    private val defaultSeedDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }

    /**
     * Creates a `crypto_version = 2` vault with [password] and [hint] via
     * [VaultRepository.createVault] and opens the session on the returned DEK so subsequent
     * record seeds and the UI password login share the same key.
     */
    suspend fun seedVaultAndOpenSession(password: String, hint: String) {
        val currentEntryPoint = entryPoint
        val dek = currentEntryPoint.vaultRepository().createVault(password.toCharArray(), hint)
        lastSeededDek = dek.copyOf()
        currentEntryPoint.sessionKeyHolder().setSessionKey(dek)
    }

    /**
     * Wraps the DEK of the most recent [seedVaultAndOpenSession] under a fresh `KEK_Biometric`
     * (a JCE pair through the test `FakeKeystoreGateway`, so no enrolled fingerprint is needed)
     * and stores `wrapped_dek_biometric`, which is what makes the login screen offer the
     * fingerprint path (design §6.6).
     */
    suspend fun seedBiometricWrap() {
        val dek = requireNotNull(lastSeededDek) { "seedVaultAndOpenSession must run first" }
        val currentEntryPoint = entryPoint
        val blob = currentEntryPoint.envelopeEncryptionManager()
            .wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, dek.copyOf())
        currentEntryPoint.vaultRepository().storeWrap(WrapKeySpec.BIOMETRIC, blob)
    }

    /**
     * Ensures a DEK session is open for seeding records when no session is currently active,
     * reusing the DEK from the most recent [seedVaultAndOpenSession] if available.
     */
    fun openSessionForSeeding() {
        val holder = entryPoint.sessionKeyHolder()
        if (holder.isUnlocked.value) return
        val dek = (lastSeededDek ?: defaultSeedDek).copyOf()
        holder.setSessionKey(dek)
    }

    /**
     * Locks the vault the way an explicit lock does: the key is cleared and no reason is recorded,
     * so the login screen that follows shows no banner. The away timeout is driven through
     * `ActiveSessionManager` instead, which records `AWAY_TIMEOUT` first.
     */
    fun lockVault() {
        entryPoint.sessionKeyHolder().clearSessionKey()
    }
}
