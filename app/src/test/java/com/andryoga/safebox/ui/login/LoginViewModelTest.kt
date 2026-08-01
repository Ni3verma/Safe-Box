@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.login

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @MockK(relaxUnitFun = true)
    lateinit var userDetailsRepository: UserDetailsRepository

    @RelaxedMockK
    lateinit var workManager: WorkManager

    @MockK
    lateinit var lazyWorkManager: Lazy<WorkManager>

    @RelaxedMockK
    lateinit var symmetricKeyUtils: SymmetricKeyUtils

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    @MockK(relaxUnitFun = true)
    lateinit var analyticsHelper: AnalyticsHelper

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: LoginViewModel


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { lazyWorkManager.get() } returns workManager
        coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns false
        viewModel = LoginViewModel(
            userDetailsRepository = userDetailsRepository,
            workManager = lazyWorkManager,
            symmetricKeyUtils = symmetricKeyUtils,
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
            isDebug = true
        )
    }

    @Test
    fun `initialUiState_whenIsDebugTrue_prefillsDebugPassword`() {
        val uiState = viewModel.uiState.value

        assertThat(uiState.userAuthState).isEqualTo(UserAuthState.INITIAL)
        assertThat(uiState.hint).isEmpty()
        assertThat(uiState.canUnlockWithBiometric).isFalse()
        assertThat(uiState.defaultPassword).isEqualTo("Qwerty@@135")
    }

    @Test
    fun `initialUiState_whenIsDebugFalse_defaultPasswordShouldBeEmpty`() {
        val prodViewModel = LoginViewModel(
            userDetailsRepository = userDetailsRepository,
            workManager = lazyWorkManager,
            symmetricKeyUtils = symmetricKeyUtils,
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
            isDebug = false
        )

        val uiState = prodViewModel.uiState.value
        assertThat(uiState.defaultPassword).isEmpty()
    }

    @Test
    fun `onBiometricAvailable updates state when biometric auth should start`() = runTest {
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.BiometricAvailable)
            advanceUntilIdle()

            coVerify(exactly = 1) { userDetailsRepository.shouldStartBiometricAuthFlow() }
            val updated = expectMostRecentItem()
            assertThat(updated.canUnlockWithBiometric).isTrue()
        }
    }

    @Test
    fun `onBiometricAvailable does not update state when biometric auth should not start`() =
        runTest {
            coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns false

            viewModel.uiState.test {
                val initial = awaitItem()
                assertThat(initial.canUnlockWithBiometric).isFalse()

                viewModel.onAction(LoginScreenAction.BiometricAvailable)
                advanceUntilIdle()

                coVerify(exactly = 1) { userDetailsRepository.shouldStartBiometricAuthFlow() }
                expectNoEvents()
                assertThat(initial.canUnlockWithBiometric).isFalse()
            }
        }

    @Test
    fun `onBiometricSuccess updates auth state and calls repository`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.BiometricSuccess)
            advanceUntilIdle()

            coVerify { userDetailsRepository.onAuthSuccess(withBiometric = true) }
            val updated = expectMostRecentItem()
            assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
        }
    }

    @Test
    fun `onLoginClicked with correct password verifies user auth state`() =
        runTest {
            val password = "password"

            coEvery { userDetailsRepository.checkPassword(password) } returns true

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(password))
                advanceUntilIdle()

                coVerify { userDetailsRepository.checkPassword(password) }
                coVerify { userDetailsRepository.onAuthSuccess(withBiometric = false) }
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }

    @Test
    fun `onLoginClicked with correct password and auto backup enabled starts backup work`() =
        runTest {
            val password = "password"
            val encryptedPassword = "encryptedPassword"

            coEvery { userDetailsRepository.checkPassword(password) } returns true
            coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns true
            every { symmetricKeyUtils.encrypt(password) } returns encryptedPassword

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(password))
                advanceUntilIdle()

                coVerify { userDetailsRepository.checkPassword(password) }
                val requestSlot = slot<OneTimeWorkRequest>()
                verify(exactly = 1) {
                    workManager.enqueueUniqueWork(
                        eq(CommonConstants.WORKER_NAME_BACKUP_DATA),
                        eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
                        capture(requestSlot)
                    )
                }
                verify { symmetricKeyUtils.encrypt(password) }
                assertThat(
                    requestSlot.captured.workSpec.input.getString(CommonConstants.BACKUP_PARAM_PASSWORD)
                ).isEqualTo(encryptedPassword)
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }

    @Test
    fun `onLoginClicked with correct password and auto backup disabled does not trigger backup`() =
        runTest {
            val password = "password"
            coEvery { userDetailsRepository.checkPassword(password) } returns true

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(password))
                advanceUntilIdle()

                coVerify(exactly = 1) { userDetailsRepository.checkPassword(password) }
                verify(exactly = 0) {
                    workManager.enqueueUniqueWork(
                        eq(CommonConstants.WORKER_NAME_BACKUP_DATA),
                        any(),
                        any<OneTimeWorkRequest>()
                    )
                }
                coVerify { userDetailsRepository.onAuthSuccess(withBiometric = false) }
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }

    @Test
    fun `onLoginClicked with incorrect password updates state and logs event`() = runTest {
        val password = "wrong_password"
        coEvery { userDetailsRepository.checkPassword(password) } returns false

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.LoginClicked(password))
            advanceUntilIdle()

            coVerify { userDetailsRepository.checkPassword(password) }
            verify { analyticsHelper.logEvent(AnalyticsKey.LOGIN_FAILED) }
            val updated = expectMostRecentItem()
            assertThat(updated.userAuthState).isEqualTo(UserAuthState.INCORRECT_PASSWORD_ENTERED)
            coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
        }
    }

    @Test
    fun `onLoginClicked with incorrect password does not trigger backup work`() = runTest {
        val password = "wrong_password"
        coEvery { userDetailsRepository.checkPassword(password) } returns false

        viewModel.onAction(LoginScreenAction.LoginClicked(password))
        advanceUntilIdle()

        coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `onShowHintClicked fetches and displays hint`() = runTest {
        val hint = "this is a hint"
        coEvery { userDetailsRepository.getHint() } returns hint

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.ShowHintClicked)
            advanceUntilIdle()

            coVerify { userDetailsRepository.getHint() }
            val updated = expectMostRecentItem()
            assertThat(updated.hint).isEqualTo(hint)
        }
    }

    @Test
    fun `onShowHintClicked with null hint keeps hint empty`() = runTest {
        coEvery { userDetailsRepository.getHint() } returns null

        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.hint).isEmpty()

            viewModel.onAction(LoginScreenAction.ShowHintClicked)
            advanceUntilIdle()

            coVerify { userDetailsRepository.getHint() }
            expectNoEvents()
        }
    }

    @Test
    fun `onResetPassword updates repository and verifies user auth state`() =
        runTest {
            coEvery { userDetailsRepository.updatePasswordAndHint(any(), any()) } returns Unit

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(
                    LoginScreenAction.OnResetPassword(
                        newPassword = "NewPassword@@123",
                        hint = "new hint"
                    )
                )
                advanceUntilIdle()

                coVerify {
                    userDetailsRepository.updatePasswordAndHint("NewPassword@@123", "new hint")
                }
                coVerify { userDetailsRepository.onAuthSuccess(withBiometric = false) }
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }
}
