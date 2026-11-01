package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.KeyStoreException
import java.security.ProviderException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-unlock background maintenance for `crypto_version = 2` vaults.
 *
 * Silently creates or heals `KEK_Device` and `KEK_Biometric` wraps without showing a prompt
 * (wrapping uses only the public half of each RSA-2048 key pair), rotates the per-generation alias
 * suffix when a Keystore entry is invalidated or stuck, and deletes any leftover legacy
 * `symmetricDataKey` alias.
 */
@Singleton
class VaultKeyMaintenance @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder,
    private val vaultRepository: VaultRepository,
    private val vaultMetaDao: VaultMetaDao,
    private val keystoreGateway: KeystoreGateway,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val analyticsHelper: AnalyticsHelper,
    private val dispatchersProvider: DispatchersProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /**
     * Launches [runAfterUnlock] in the application scope so UI callers do not block navigation or
     * cancel maintenance when the login or signup ViewModel clears.
     */
    fun scheduleAfterUnlock() {
        applicationScope.launch(dispatchersProvider.default) {
            runAfterUnlock()
        }
    }

    /**
     * Runs one serialized maintenance pass over the active envelope session.
     *
     * Returns immediately if the vault is in a legacy session, is not at `crypto_version = 2`, or
     * locks before the DEK is read.
     */
    suspend fun runAfterUnlock() {
        withContext(dispatchersProvider.default) {
            mutex.withLock {
                try {
                    runLocked()
                } catch (c: CancellationException) {
                    throw c
                } catch (_: SessionLockedException) {
                    // Session locked mid-maintenance; abort quietly.
                } catch (e: Exception) {
                    Timber.w(e, "post-unlock key maintenance failed")
                }
            }
        }
    }

    private suspend fun runLocked() {
        if (sessionKeyHolder.isLegacySession) return
        if (vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) != CryptoVersion.ENVELOPE) return

        val dek = try {
            sessionKeyHolder.withSessionKey { key -> key.encoded }
        } catch (_: SessionLockedException) {
            return
        }

        try {
            val snapshot = runCatching { deviceSecurityStatus.snapshot() }.getOrNull() ?: return

            if (snapshot.isDeviceSecure) {
                ensureWrap(spec = WrapKeySpec.DEVICE, dek = dek)
            } else if (vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) != null) {
                Timber.i("device lock screen removed; deleting device wrap")
                vaultRepository.deleteWrap(WrapKeySpec.DEVICE)
            }

            val biometricDisabled =
                vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) == BIOMETRIC_DISABLED_VALUE
            if (snapshot.biometricClass == BiometricClass.STRONG && !biometricDisabled) {
                ensureWrap(spec = WrapKeySpec.BIOMETRIC, dek = dek)
            } else if (vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) != null) {
                Timber.i("strong biometric unavailable or disabled; deleting biometric wrap")
                vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC)
            }

            // TODO(D3): legacy vault (crypto_version = 1) only — removes any leftover legacy alias
            keystoreGateway.deleteLegacyVaultKey()
        } finally {
            dek.fill(0)
        }
    }

    private suspend fun ensureWrap(spec: WrapKeySpec, dek: ByteArray) {
        val currentSuffix = vaultRepository.getWrapKeyAliasSuffix(spec)
        val currentAlias = EnvelopeEncryptionManagerImpl.aliasFor(spec, currentSuffix)
        val existingWrap = vaultMetaDao.get(spec.wrappedDekKey)
        val health = keystoreGateway.probe(currentAlias)

        if (!existingWrap.isNullOrBlank() && health == KeyHealth.Healthy) {
            return
        }

        val initialSuffix = if (health == KeyHealth.Invalidated) {
            val rotated = nextAliasSuffix(currentSuffix)
            Timber.i(
                "Keystore key %s invalidated; rotating suffix to %s",
                currentAlias,
                rotated,
            )
            keystoreGateway.deleteKey(currentAlias)
            rotated
        } else {
            currentSuffix
        }

        val wrapName = spec.wireName
        try {
            val (blob, usedSuffix) = wrapWithStuckAliasFallback(
                spec = spec,
                dek = dek,
                currentSuffix = currentSuffix,
                preferredSuffix = initialSuffix,
            )
            // Verify the session is still open before committing the new wrap to vault_meta.
            sessionKeyHolder.withSessionKey { }
            vaultRepository.storeWrap(spec = spec, blob = blob, aliasSuffix = usedSuffix)
            Timber.i("created %s wrap during post-unlock maintenance", wrapName)
            analyticsHelper.logEvent(AnalyticsKey.VAULT_WRAP_CREATED) {
                param(AnalyticsParam.WRAP, wrapName)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: SessionLockedException) {
            // Session locked mid-maintenance; abort quietly without logging a wrap failure.
        } catch (e: Exception) {
            Timber.w(e, "Failed to create %s wrap during post-unlock maintenance", wrapName)
            analyticsHelper.logEvent(AnalyticsKey.VAULT_WRAP_CREATE_FAILURE) {
                param(AnalyticsParam.WRAP, wrapName)
                param(AnalyticsParam.REASON, classifyWrapFailureReason(e))
                param(AnalyticsParam.EXCEPTION, e.javaClass.simpleName)
            }
        }
    }

    private fun wrapWithStuckAliasFallback(
        spec: WrapKeySpec,
        dek: ByteArray,
        currentSuffix: String?,
        preferredSuffix: String?,
    ): Pair<String, String?> {
        return try {
            val blob = envelopeEncryptionManager.wrapWithKeystoreKey(spec, dek, preferredSuffix)
            blob to preferredSuffix
        } catch (e: Exception) {
            // If a stuck Keystore entry on the current suffix refused generation, advance to a fresh suffix once.
            if ((e is KeyStoreException || e is ProviderException) && preferredSuffix == currentSuffix) {
                val rotatedSuffix = nextAliasSuffix(currentSuffix)
                Timber.i(
                    "Keystore alias for %s stuck on suffix %s; rotating to %s",
                    spec.wireName,
                    currentSuffix ?: "default",
                    rotatedSuffix,
                )
                val blob = envelopeEncryptionManager.wrapWithKeystoreKey(spec, dek, rotatedSuffix)
                blob to rotatedSuffix
            } else {
                throw e
            }
        }
    }

    companion object {
        private const val BIOMETRIC_DISABLED_VALUE = "1"

        /**
         * Returns the next monotonic generation suffix (`"1"`, `"2"`, ...) after [currentSuffix].
         */
        internal fun nextAliasSuffix(currentSuffix: String?): String {
            val current = currentSuffix?.toIntOrNull() ?: 0
            return (current + 1).toString()
        }

        internal fun classifyWrapFailureReason(error: Throwable): String = when (error) {
            is LockScreenRequiredException -> "lock_screen_required"
            is BiometricNotEnrolledException -> "biometric_not_enrolled"
            is KeyStoreException, is ProviderException -> "keystore"
            else -> "unknown"
        }
    }
}
