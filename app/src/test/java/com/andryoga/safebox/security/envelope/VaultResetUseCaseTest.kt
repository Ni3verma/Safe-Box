@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.security.envelope

import androidx.work.WorkManager
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [VaultResetUseCase]: the erase sequence and its ordering.
 */
class VaultResetUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var database: SafeBoxDatabase

    @MockK(relaxUnitFun = true)
    lateinit var vaultRepository: VaultRepository

    @RelaxedMockK
    lateinit var workManager: WorkManager

    @MockK(relaxUnitFun = true)
    lateinit var preferenceProvider: PreferenceProvider

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var keystoreGateway: FakeKeystoreGateway
    private lateinit var useCase: VaultResetUseCase

    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        sessionKeyHolder = SessionKeyHolderImpl()
        keystoreGateway = FakeKeystoreGateway().apply { legacyKey = CryptoTestFixtures.TEST_AES_KEY_256 }
        coEvery { vaultRepository.getWrapKeyAliasSuffix(any()) } returns null
        useCase = VaultResetUseCase(
            database = Lazy { database },
            vaultRepository = vaultRepository,
            sessionKeyHolder = sessionKeyHolder,
            keystoreGateway = keystoreGateway,
            workManager = Lazy { workManager },
            preferenceProvider = preferenceProvider,
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
        )
    }

    @Test
    fun eraseVault_shouldCancelBothWorkersLockTheSessionAndClearEveryTable() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())

        useCase.eraseVault()

        verify(exactly = 1) { workManager.cancelUniqueWork(CommonConstants.WORKER_NAME_BACKUP_DATA) }
        verify(exactly = 1) { workManager.cancelUniqueWork(CommonConstants.WORKER_NAME_RESTORE_DATA) }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 1) { database.clearAllTables() }
        coVerify(exactly = 1) {
            preferenceProvider.removePrefByKey(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING)
        }
    }

    @Test
    fun eraseVault_shouldLockTheSessionBeforeTheTablesGo() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        var unlockedWhenTablesCleared: Boolean? = null
        every { database.clearAllTables() } answers {
            unlockedWhenTablesCleared = sessionKeyHolder.isUnlocked.value
        }

        useCase.eraseVault()

        // A record Flow collected during the erase must fail closed, never read an emptied table.
        assertThat(unlockedWhenTablesCleared).isFalse()
    }

    @Test
    fun eraseVault_shouldDeleteBothWrapKeysUnderTheirStoredSuffixesAndTheLegacyKey() = runTest {
        coEvery { vaultRepository.getWrapKeyAliasSuffix(WrapKeySpec.BIOMETRIC) } returns "2"
        coEvery { vaultRepository.getWrapKeyAliasSuffix(WrapKeySpec.DEVICE) } returns null
        val biometricAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, "2")
        val deviceAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE, null)
        keystoreGateway.generateWrapKeyPair(biometricAlias, WrapKeySpec.BIOMETRIC)
        keystoreGateway.generateWrapKeyPair(deviceAlias, WrapKeySpec.DEVICE)

        useCase.eraseVault()

        assertThat(keystoreGateway.probe(biometricAlias)).isEqualTo(KeyHealth.Missing)
        assertThat(keystoreGateway.probe(deviceAlias)).isEqualTo(KeyHealth.Missing)
        assertThat(keystoreGateway.legacyVaultKey()).isNull()
    }

    @Test
    fun eraseVault_whenAlreadyLocked_shouldStillClearEverything() = runTest {
        useCase.eraseVault()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 1) { database.clearAllTables() }
        assertThat(keystoreGateway.legacyVaultKey()).isNull()
    }

    @Test
    fun eraseVault_whenWorkManagerThrows_shouldStillEraseTheVault() = runTest {
        every { workManager.cancelUniqueWork(any()) } throws IllegalStateException("WorkManager not initialised")
        sessionKeyHolder.setSessionKey(testDek.copyOf())

        useCase.eraseVault()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 1) { database.clearAllTables() }
    }
}
