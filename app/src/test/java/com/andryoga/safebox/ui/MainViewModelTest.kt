@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.ui.loading.LoadingState
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    @MockK
    lateinit var vaultRepository: VaultRepository

    private lateinit var viewModel: MainViewModel
    private lateinit var sessionKeyHolder: SessionKeyHolderImpl

    private val isPrivacyEnabledFlow = MutableStateFlow(true)
    private val backupMetadataFlow = MutableStateFlow<BackupPathData?>(null)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.isPrivacyEnabledFlow } returns isPrivacyEnabledFlow
        every { backupMetadataRepository.getBackupMetadata() } returns backupMetadataFlow
        sessionKeyHolder = SessionKeyHolderImpl()

        viewModel = MainViewModel(
            backupMetadataRepository,
            settingsDataStore,
            vaultRepository,
            sessionKeyHolder,
        )
    }

    @Test
    fun `isBackupPathSet state is false when backup metadata is null`() = runTest {
        viewModel.isBackupPathSet.test {
            assertThat(awaitItem()).isTrue()
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `isBackupPathSet state is true when backup metadata is not null`() = runTest {
        backupMetadataFlow.emit(null)
        viewModel.isBackupPathSet.test {
            assertThat(awaitItem()).isTrue() // initial seeded value
            assertThat(awaitItem()).isFalse() // derived state from null metadata
            backupMetadataFlow.emit(BackupPathData("uri_path", "file_name", ""))
            assertThat(awaitItem()).isTrue() // derived state from BackupPathData
        }
    }

    @Test
    fun isVaultUnlocked_whenNoSessionIsOpen_shouldBeFalse() {
        assertThat(viewModel.isVaultUnlocked.value).isFalse()
    }

    @Test
    fun isVaultUnlocked_inALegacySession_shouldBeTrue() {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)

        assertThat(viewModel.isVaultUnlocked.value).isTrue()
    }

    @Test
    fun isVaultUnlocked_whenTheKeyIsClearedWhileCollected_shouldEmitFalse() = runTest {
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 1 })

        viewModel.isVaultUnlocked.test {
            assertThat(awaitItem()).isTrue()

            sessionKeyHolder.clearSessionKey()

            assertThat(awaitItem()).isFalse()
        }
    }

    /**
     * Routing reads Room alone: the ViewModel has no preference dependency left, so
     * `IS_SIGN_UP_REQUIRED` cannot steer an existing vault into signup whatever it says.
     */
    @Test
    fun loadingState_whenAVaultExists_shouldProceedToLogin() = runTest {
        coEvery { vaultRepository.hasVault() } returns true

        viewModel.loadingState.test {
            assertThat(awaitItem()).isEqualTo(LoadingState.Initial)
            assertThat(awaitItem()).isEqualTo(LoadingState.ProceedToLogin)
        }
    }

    @Test
    fun loadingState_whenNoVaultExists_shouldProceedToSignup() = runTest {
        coEvery { vaultRepository.hasVault() } returns false

        viewModel.loadingState.test {
            assertThat(awaitItem()).isEqualTo(LoadingState.Initial)
            assertThat(awaitItem()).isEqualTo(LoadingState.ProceedToSignup)
        }
    }

    @Test
    fun `isPrivacyEnabled emits true when settings emits true`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `isPrivacyEnabled emits false when settings emits false`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
            isPrivacyEnabledFlow.emit(false)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `isPrivacyEnabled does not emit duplicate value when settings emits prev value`() =
        runTest {
            viewModel.isPrivacyEnabled.test {
                assertThat(awaitItem()).isTrue()
                isPrivacyEnabledFlow.emit(false)
                assertThat(awaitItem()).isFalse()

                isPrivacyEnabledFlow.emit(false) // false again
                advanceUntilIdle()
                expectNoEvents() // no duplicate emission
            }
        }

    @Test
    fun `isPrivacyEnabled emit updated value when settings emits new value`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
            isPrivacyEnabledFlow.emit(false)
            assertThat(awaitItem()).isFalse()
            isPrivacyEnabledFlow.emit(true) // changed
            assertThat(awaitItem()).isTrue()
        }
    }
}
