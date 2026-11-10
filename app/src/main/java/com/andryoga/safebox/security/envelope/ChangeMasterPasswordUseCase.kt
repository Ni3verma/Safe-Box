package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Change master password on an open envelope vault: the current-password gate and
 * the re-wrap. The DEK does not change, so no record is re-encrypted and the Keystore wraps stay.
 * Not offered in a legacy session (there is no DEK to re-wrap).
 */
class ChangeMasterPasswordUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val sessionKeyHolder: SessionKeyHolder,
    private val preferenceProvider: PreferenceProvider,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchersProvider: DispatchersProvider,
) {
    /**
     * Checks [password] against `wrapped_dek_password` and the verifier, the same proof a password
     * login requires. Defensive: any failure answers `false`.
     *
     * @param password Candidate current master password; zeroed before this returns.
     * @return `true` only when [password] unwraps the vault's real DEK.
     */
    suspend fun verifyCurrentPassword(password: CharArray): Boolean {
        val state = try {
            vaultRepository.getVaultState()
        } catch (c: CancellationException) {
            password.fill(Char.MIN_VALUE)
            throw c
        } catch (e: Exception) {
            password.fill(Char.MIN_VALUE)
            Timber.w(e, "vault state unreadable during the change-password gate")
            return false
        }
        if (state !is VaultState.Envelope) {
            password.fill(Char.MIN_VALUE)
            // Callers offer the change only on an open envelope vault; a corrupted one cannot be open.
            Utils.crashInDebugBuild("change-password gate reached on a non-envelope vault: $state")
            return false
        }
        return withContext(dispatchersProvider.default) {
            val dek = envelopeEncryptionManager.unwrapWithPassword(
                password = password,
                params = state.kdfParams,
                blob = state.wrappedDekPassword,
            ).getOrNull() ?: return@withContext false
            try {
                envelopeEncryptionManager.verify(dek, state.dekVerifier)
            } finally {
                dek.fill(0)
            }
        }
    }

    /**
     * Re-wraps the session DEK under [newPassword] and stores [hint], in one transaction, then
     * resets the password-after-biometric counter as a password login would. Operational: storage
     * failures propagate to the caller, which owns the user-facing recovery.
     *
     * @param newPassword New master password; zeroed before this returns.
     * @param hint New hint, stored in plaintext.
     * @throws SessionLockedException When the vault locked before the DEK was read.
     */
    suspend fun changePassword(newPassword: CharArray, hint: String) {
        check(!sessionKeyHolder.isLegacySession) { "change password is not offered in a legacy session" }
        val dek = try {
            sessionKeyHolder.withSessionKey { key -> key.encoded }
        } catch (e: SessionLockedException) {
            newPassword.fill(Char.MIN_VALUE)
            throw e
        }
        try {
            vaultRepository.rewrapPassword(newPassword, dek, hint)
        } finally {
            dek.fill(0)
            newPassword.fill(Char.MIN_VALUE)
        }
        Timber.i("master password changed")
        preferenceProvider.upsertIntPref(
            CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
            settingsDataStore.getPasswordAfterXBiometricLogins(),
        )
    }
}
