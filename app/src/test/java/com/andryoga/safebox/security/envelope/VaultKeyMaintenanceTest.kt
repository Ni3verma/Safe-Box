@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.KeyStoreException
import java.security.ProviderException

/**
 * Unit tests for [VaultKeyMaintenance] verifying silent wrap creation, invalidation healing,
 * per-generation alias suffix rotation, legacy key cleanup, and wrap analytics.
 */
class VaultKeyMaintenanceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var vaultRepository: VaultRepository

    @MockK(relaxUnitFun = true)
    lateinit var vaultMetaDao: VaultMetaDao

    @MockK
    lateinit var deviceSecurityStatus: DeviceSecurityStatus

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var fakeKeystoreGateway: FakeKeystoreGateway
    private lateinit var envelopeEncryptionManager: EnvelopeEncryptionManager
    private lateinit var fakeAnalytics: FakeAnalyticsHelper
    private lateinit var maintenance: VaultKeyMaintenance

    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        sessionKeyHolder = SessionKeyHolderImpl()
        fakeKeystoreGateway = FakeKeystoreGateway()
        envelopeEncryptionManager = spyk(EnvelopeEncryptionManagerImpl(fakeKeystoreGateway))
        fakeAnalytics = FakeAnalyticsHelper()

        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) } returns null
        coEvery { vaultRepository.getWrapKeyAliasSuffix(any()) } returns null
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.STRONG,
            hasStrongBox = false,
        )

        maintenance = VaultKeyMaintenance(
            sessionKeyHolder = sessionKeyHolder,
            vaultRepository = vaultRepository,
            vaultMetaDao = vaultMetaDao,
            keystoreGateway = fakeKeystoreGateway,
            envelopeEncryptionManager = envelopeEncryptionManager,
            deviceSecurityStatus = deviceSecurityStatus,
            analyticsHelper = fakeAnalytics,
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
            applicationScope = CoroutineScope(mainDispatcherRule.testDispatcher),
        )
    }

    @Test
    fun runAfterUnlock_whenSessionIsLocked_shouldDoNothing() = runTest {
        maintenance.runAfterUnlock()

        coVerify(exactly = 0) { vaultRepository.storeWrap(any(), any(), any()) }
        assertThat(fakeAnalytics.loggedEvents).isEmpty()
    }

    @Test
    fun runAfterUnlock_whenInLegacySession_shouldDoNothing() = runTest {
        val legacyKey = fakeKeystoreGateway.getOrCreateLegacyVaultKey()
        sessionKeyHolder.setLegacySession(legacyKey)

        maintenance.runAfterUnlock()

        coVerify(exactly = 0) { vaultRepository.storeWrap(any(), any(), any()) }
        assertThat(fakeKeystoreGateway.legacyVaultKey()).isNotNull()
        assertThat(fakeAnalytics.loggedEvents).isEmpty()
    }

    @Test
    fun runAfterUnlock_whenDeviceSecureAndBiometricStrong_shouldCreateBothWrapsAndDeleteLegacyKey() = runTest {
        fakeKeystoreGateway.getOrCreateLegacyVaultKey()
        sessionKeyHolder.setSessionKey(testDek.copyOf())

        maintenance.runAfterUnlock()

        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.DEVICE, any(), null)
        }
        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.BIOMETRIC, any(), null)
        }
        assertThat(fakeKeystoreGateway.legacyVaultKey()).isNull()
        val createdEvents = fakeAnalytics.loggedEvents.filter {
            it.key == AnalyticsKey.VAULT_WRAP_CREATED
        }
        assertThat(createdEvents.map { it.params[AnalyticsParam.WRAP.paramName] })
            .containsExactly("device", "biometric")
    }

    @Test
    fun runAfterUnlock_whenWrapsAlreadyExistAndKeysHealthy_shouldSkipCreation() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, testDek)
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, testDek)
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns "existing-device-blob"
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns "existing-bio-blob"

        maintenance.runAfterUnlock()

        coVerify(exactly = 0) { vaultRepository.storeWrap(any(), any(), any()) }
        assertThat(fakeAnalytics.hasLogged(AnalyticsKey.VAULT_WRAP_CREATED)).isFalse()
    }

    @Test
    fun runAfterUnlock_whenKeyInvalidated_shouldDeleteOldKeyRotateAliasSuffixAndStoreNewWrap() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        val baseAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, null)
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, testDek)
        fakeKeystoreGateway.invalidateKey(baseAlias)
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns "healthy-device-blob"
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, testDek)
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns "stale-bio-blob"

        maintenance.runAfterUnlock()

        assertThat(fakeKeystoreGateway.probe(baseAlias)).isEqualTo(KeyHealth.Missing)
        val rotatedAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, "1")
        assertThat(fakeKeystoreGateway.probe(rotatedAlias)).isEqualTo(KeyHealth.Healthy)
        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.BIOMETRIC, any(), "1")
        }
    }

    @Test
    fun runAfterUnlock_whenStuckKeystoreAliasThrowsKeyStoreException_shouldRotateSuffixAndRetry() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = false,
        )
        coEvery { vaultRepository.getWrapKeyAliasSuffix(WrapKeySpec.DEVICE) } returns "2"
        every {
            envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, any(), "2")
        } throws KeyStoreException("Stuck alias safebox_kek_device_2")

        maintenance.runAfterUnlock()

        val rotatedAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE, "3")
        assertThat(fakeKeystoreGateway.probe(rotatedAlias)).isEqualTo(KeyHealth.Healthy)
        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.DEVICE, any(), "3")
        }
        assertThat(fakeAnalytics.hasLogged(AnalyticsKey.VAULT_WRAP_CREATED)).isTrue()
    }

    @Test
    fun runAfterUnlock_whenStuckKeystoreAliasThrowsProviderException_shouldRotateSuffixAndRetry() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = false,
        )
        coEvery { vaultRepository.getWrapKeyAliasSuffix(WrapKeySpec.DEVICE) } returns "2"
        every {
            envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, any(), "2")
        } throws ProviderException("Keystore driver stuck on safebox_kek_device_2")

        maintenance.runAfterUnlock()

        val rotatedAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE, "3")
        assertThat(fakeKeystoreGateway.probe(rotatedAlias)).isEqualTo(KeyHealth.Healthy)
        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.DEVICE, any(), "3")
        }
        assertThat(fakeAnalytics.hasLogged(AnalyticsKey.VAULT_WRAP_CREATED)).isTrue()
    }

    @Test
    fun runAfterUnlock_whenDeviceLockRemovedAndDeviceWrapExists_shouldDeleteDeviceWrap() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = false,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = false,
        )
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns "stale-device-wrap"

        maintenance.runAfterUnlock()

        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.DEVICE) }
        coVerify(exactly = 0) { vaultRepository.storeWrap(any(), any(), any()) }
    }

    @Test
    fun runAfterUnlock_whenBiometricUnlockDisabledAndBiometricWrapExists_shouldDeleteBiometricWrap() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) } returns "1"
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns "disabled-bio-wrap"
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, testDek)
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns "healthy-device-wrap"

        maintenance.runAfterUnlock()

        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC) }
        coVerify(exactly = 0) { vaultRepository.storeWrap(WrapKeySpec.BIOMETRIC, any(), any()) }
    }

    @Test
    fun runAfterUnlock_whenStrongBiometricUnenrolledAndBiometricWrapExists_shouldDeleteBiometricWrap() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = false,
        )
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns "stale-bio-wrap"
        envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, testDek)
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns "healthy-device-wrap"

        maintenance.runAfterUnlock()

        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC) }
        coVerify(exactly = 0) { vaultRepository.storeWrap(WrapKeySpec.BIOMETRIC, any(), any()) }
    }

    @Test
    fun runAfterUnlock_whenWrapCreationFails_shouldLogVaultWrapCreateFailureAndContinue() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        every {
            envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, any(), any())
        } throws LockScreenRequiredException(IllegalStateException("Lock screen removed mid-wrap"))

        maintenance.runAfterUnlock()

        val failureEvent = fakeAnalytics.loggedEvents.firstOrNull {
            it.key == AnalyticsKey.VAULT_WRAP_CREATE_FAILURE
        }
        assertThat(failureEvent).isNotNull()
        assertThat(failureEvent!!.params[AnalyticsParam.WRAP.paramName]).isEqualTo("device")
        assertThat(failureEvent.params[AnalyticsParam.REASON.paramName]).isEqualTo("lock_screen_required")
        assertThat(failureEvent.params[AnalyticsParam.EXCEPTION.paramName])
            .isEqualTo("LockScreenRequiredException")
        // Biometric wrap should still proceed independently.
        coVerify(exactly = 1) {
            vaultRepository.storeWrap(WrapKeySpec.BIOMETRIC, any(), null)
        }
    }

    @Test
    fun runAfterUnlock_whenOuterMaintenanceStepThrows_shouldNotEscapeScope() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } throws IllegalStateException("DB read failed")

        maintenance.runAfterUnlock()

        coVerify(exactly = 0) { vaultRepository.storeWrap(any(), any(), any()) }
    }
}
