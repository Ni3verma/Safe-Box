package com.andryoga.safebox.security.envelope

import androidx.work.WorkManager
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import dagger.Lazy
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * *Erase everything and start over* (design §6.11): the one path that destroys a vault on purpose.
 *
 * Reachable only from the two login-screen states that have no other way forward — recovery not
 * set up on a v2 vault, and a legacy vault whose Keystore key is gone. Manual `.bak` files on
 * external storage are untouched, so the user can restore one after signing up again.
 */
class VaultResetUseCase @Inject constructor(
    private val database: Lazy<SafeBoxDatabase>,
    private val vaultRepository: VaultRepository,
    private val sessionKeyHolder: SessionKeyHolder,
    private val keystoreGateway: KeystoreGateway,
    private val workManager: Lazy<WorkManager>,
    private val preferenceProvider: PreferenceProvider,
    private val dispatchersProvider: DispatchersProvider,
) {
    /**
     * Cancels pending backup/restore work, locks the session, deletes every Keystore key of the
     * vault (both wrap-key generations in use and the legacy alias), clears every Room table and
     * resets the biometric login counter. After it returns `VaultRepository.hasVault()` is `false`,
     * so the Room-first start routing lands on Signup.
     *
     * The wrap-key aliases are resolved from `vault_meta` before the tables go; the Keystore
     * deletes are best effort (a stuck entry is harmless without its wrap).
     */
    suspend fun eraseVault() {
        Timber.w("erasing vault on user request")
        runCatching {
            workManager.get().cancelUniqueWork(CommonConstants.WORKER_NAME_BACKUP_DATA)
            workManager.get().cancelUniqueWork(CommonConstants.WORKER_NAME_RESTORE_DATA)
        }
        sessionKeyHolder.clearSessionKey()
        val wrapKeyAliases = WrapKeySpec.entries.map { spec ->
            EnvelopeEncryptionManagerImpl.aliasFor(spec, vaultRepository.getWrapKeyAliasSuffix(spec))
        }
        wrapKeyAliases.forEach(keystoreGateway::deleteKey)
        // TODO(D3): legacy vault (crypto_version = 1) only
        keystoreGateway.deleteLegacyVaultKey()
        // clearAllTables is blocking by contract (Room refuses it on the main thread).
        withContext(dispatchersProvider.io) {
            database.get().clearAllTables()
        }
        preferenceProvider.removePrefByKey(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING)
        Timber.w("vault erased")
    }
}
