@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.login

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.AnalyticsParamsBuilder
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.EnvelopeEncryptionManagerImpl
import com.andryoga.safebox.security.envelope.KdfParams
import com.andryoga.safebox.security.envelope.PasswordKdf
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.andryoga.safebox.security.envelope.VaultResetUseCase
import com.andryoga.safebox.security.envelope.migration.LegacyVaultMigrator
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationOutcome
import com.andryoga.safebox.security.envelope.migration.MigrationStage
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.security.envelope.migration.VaultMigrationProgress
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.SessionLockReason
import com.andryoga.safebox.ui.core.SessionLockReasonStore
import com.andryoga.safebox.worker.BackupPasswordRegistry
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher

class LoginViewModelTest {
    @MockK(relaxUnitFun = true)
    lateinit var userDetailsRepository: UserDetailsRepository

    @MockK(relaxUnitFun = true)
    lateinit var vaultRepository: VaultRepository

    @MockK
    lateinit var legacyVaultMigrator: LegacyVaultMigrator

    @MockK(relaxUnitFun = true)
    lateinit var vaultKeyMaintenance: VaultKeyMaintenance

    @MockK(relaxUnitFun = true)
    lateinit var vaultResetUseCase: VaultResetUseCase

    @RelaxedMockK
    lateinit var workManager: WorkManager

    @MockK
    lateinit var lazyWorkManager: Lazy<WorkManager>

    @MockK(relaxUnitFun = true)
    lateinit var settingsDataStore: SettingsDataStore

    @MockK(relaxUnitFun = true)
    lateinit var analyticsHelper: AnalyticsHelper

    @MockK(relaxUnitFun = true)
    lateinit var activeSessionManager: ActiveSessionManager

    @MockK
    lateinit var deviceSecurityStatus: DeviceSecurityStatus

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var keystoreGateway: FakeKeystoreGateway
    private lateinit var envelopeEncryptionManager: EnvelopeEncryptionManager
    private lateinit var vaultMigrationProgress: VaultMigrationProgress
    private lateinit var viewModel: LoginViewModel
    private val backupPasswordRegistry = BackupPasswordRegistry()
    private val sessionLockReasonStore = SessionLockReasonStore()

    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }
    private lateinit var defaultEnvelopeState: VaultState.Envelope

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { lazyWorkManager.get() } returns workManager
        coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns false
        coEvery { userDetailsRepository.onAuthSuccess(any()) } returns FIRST_LOGIN_ORDINAL
        every { deviceSecurityStatus.snapshot() } returns SECURE_WITH_STRONG_BIOMETRIC

        sessionKeyHolder = SessionKeyHolderImpl()
        keystoreGateway = FakeKeystoreGateway().apply { legacyKey = CryptoTestFixtures.TEST_AES_KEY_256 }
        envelopeEncryptionManager = spyk(EnvelopeEncryptionManagerImpl(keystoreGateway))
        vaultMigrationProgress = VaultMigrationProgress()

        val fastKdfParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 2_000,
            salt = ByteArray(16) { 0x11 },
        )
        val wrappedPassword = envelopeEncryptionManager.wrapWithPassword(
            password = DEFAULT_PASSWORD.toCharArray(),
            dek = testDek,
            params = fastKdfParams,
        )
        val verifier = envelopeEncryptionManager.makeVerifier(testDek)
        defaultEnvelopeState = VaultState.Envelope(
            kdfParams = KdfParams(
                algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
                iterations = PasswordKdf.PBKDF2_ITERATIONS,
                salt = ByteArray(16) { 0x11 },
            ),
            wrappedDekPassword = wrappedPassword,
            dekVerifier = verifier,
            wrappedDekDevice = null,
            wrappedDekBiometric = null,
            isBiometricUnlockDisabled = false,
            biometricKeyAliasSuffix = null,
            deviceKeyAliasSuffix = null,
        )
        // Stub unwrapWithPassword for the default password so unit tests don't pay 600k PBKDF2 cost.
        every {
            envelopeEncryptionManager.unwrapWithPassword(
                match { it.contentEquals(DEFAULT_PASSWORD.toCharArray()) },
                any(),
                eq(wrappedPassword),
            )
        } answers {
            firstArg<CharArray>().fill('\u0000')
            Result.success(testDek.copyOf())
        }

        coEvery { vaultRepository.getVaultState() } returns defaultEnvelopeState
        viewModel = createViewModel(isDebug = true)
    }

    private fun createViewModel(isDebug: Boolean) = LoginViewModel(
        userDetailsRepository = userDetailsRepository,
        vaultRepository = vaultRepository,
        envelopeEncryptionManager = envelopeEncryptionManager,
        legacyVaultMigrator = Lazy { legacyVaultMigrator },
        vaultMigrationProgress = vaultMigrationProgress,
        vaultKeyMaintenance = vaultKeyMaintenance,
        vaultResetUseCase = Lazy { vaultResetUseCase },
        workManager = lazyWorkManager,
        backupPasswordRegistry = backupPasswordRegistry,
        settingsDataStore = settingsDataStore,
        analyticsHelper = analyticsHelper,
        activeSessionManager = activeSessionManager,
        deviceSecurityStatus = deviceSecurityStatus,
        sessionKeyHolder = sessionKeyHolder,
        keystoreGateway = keystoreGateway,
        dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
        sessionLockReasonStore = sessionLockReasonStore,
        isDebug = isDebug,
    )

    /** Wraps [dek] under a fresh JCE `KEK_Biometric` and serves a vault state that carries the wrap. */
    private fun givenBiometricWrap(dek: ByteArray = testDek, isBiometricUnlockDisabled: Boolean = false): String {
        val blob = envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, dek)
        coEvery { vaultRepository.getVaultState() } returns defaultEnvelopeState.copy(
            wrappedDekBiometric = blob,
            isBiometricUnlockDisabled = isBiometricUnlockDisabled,
        )
        return blob
    }

    /** Wraps [dek] under a fresh JCE `KEK_Device` and serves a vault state that carries the wrap. */
    private fun givenDeviceWrap(dek: ByteArray = testDek): String {
        val blob = envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, dek)
        coEvery { vaultRepository.getVaultState() } returns defaultEnvelopeState.copy(wrappedDekDevice = blob)
        return blob
    }

    /** Runs the pre-conditions check and returns the cipher the screen would bind, if any. */
    private fun prepareBiometricPrompt(): Cipher? {
        viewModel.onAction(LoginScreenAction.BiometricAvailable)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.biometricUnlockCipher
    }

    /** Taps Forgot password and returns the device step the screen would prompt with. */
    private fun startDeviceRecovery(): ForgotPasswordStep.DeviceAuthentication {
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.forgotPasswordStep as ForgotPasswordStep.DeviceAuthentication
    }

    @Test
    fun initialUiState_whenNothingClearedTheSession_shouldShowNoLockReason() {
        assertThat(viewModel.uiState.value.lockReason).isNull()
    }

    @Test
    fun initialUiState_afterAnAwayTimeout_shouldShowTheReasonAndConsumeIt() {
        sessionLockReasonStore.record(SessionLockReason.AWAY_TIMEOUT)

        val afterTimeout = createViewModel(isDebug = false)

        assertThat(afterTimeout.uiState.value.lockReason).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
        // The next login screen (a later lock that recorded nothing) shows no banner.
        assertThat(createViewModel(isDebug = false).uiState.value.lockReason).isNull()
    }

    @Test
    fun initialUiState_whenIsDebugTrue_prefillsDebugPassword() {
        val uiState = viewModel.uiState.value

        assertThat(uiState.userAuthState).isEqualTo(UserAuthState.INITIAL)
        assertThat(uiState.hint).isEmpty()
        assertThat(uiState.canUnlockWithBiometric).isFalse()
        assertThat(uiState.defaultPassword).isEqualTo("Qwerty@@135")
    }

    @Test
    fun initialUiState_whenIsDebugFalse_defaultPasswordShouldBeEmpty() {
        val prodViewModel = createViewModel(isDebug = false)

        val uiState = prodViewModel.uiState.value

        assertThat(uiState.defaultPassword).isEmpty()
    }

    @Test
    fun initialUiState_whenVaultIsLegacy_setsIsLegacyVaultTrue() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy

        val legacyVm = createViewModel(isDebug = false)
        advanceUntilIdle()

        assertThat(legacyVm.uiState.value.isLegacyVault).isTrue()
    }

    // region biometric login (design §6.6)

    @Test
    fun onBiometricAvailable_whenWrapPresentAndCounterAllows_shouldPrepareTheCipher() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.BiometricAvailable)
            advanceUntilIdle()

            coVerify(exactly = 1) { userDetailsRepository.shouldStartBiometricAuthFlow() }
            val updated = expectMostRecentItem()
            assertThat(updated.canUnlockWithBiometric).isTrue()
            assertThat(updated.biometricUnlockCipher).isNotNull()
            assertThat(updated.isBiometricKeyInvalidated).isFalse()
        }
    }

    @Test
    fun onBiometricAvailable_whenNoBiometricWrapExists_shouldNotOfferBiometricLogin() = runTest {
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.BiometricAvailable)
            advanceUntilIdle()

            expectNoEvents()
            coVerify(exactly = 0) { userDetailsRepository.shouldStartBiometricAuthFlow() }
        }
    }

    @Test
    fun onBiometricAvailable_whenBiometricUnlockDisabled_shouldNotOfferBiometricLogin() = runTest {
        givenBiometricWrap(isBiometricUnlockDisabled = true)
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true

        assertThat(prepareBiometricPrompt()).isNull()
        coVerify(exactly = 0) { userDetailsRepository.shouldStartBiometricAuthFlow() }
    }

    @Test
    fun onBiometricAvailable_whenLegacyVault_suppressesBiometricPrompt() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        val legacyVm = createViewModel(isDebug = false)
        advanceUntilIdle()

        legacyVm.onAction(LoginScreenAction.BiometricAvailable)
        advanceUntilIdle()

        assertThat(legacyVm.uiState.value.isLegacyVault).isTrue()
        assertThat(legacyVm.uiState.value.canUnlockWithBiometric).isFalse()
        coVerify(exactly = 0) { userDetailsRepository.shouldStartBiometricAuthFlow() }
    }

    @Test
    fun onBiometricAvailable_doesNotUpdateStateWhenBiometricAuthShouldNotStart() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns false

        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.canUnlockWithBiometric).isFalse()

            viewModel.onAction(LoginScreenAction.BiometricAvailable)
            advanceUntilIdle()

            coVerify(exactly = 1) { userDetailsRepository.shouldStartBiometricAuthFlow() }
            expectNoEvents()
        }
    }

    @Test
    fun onBiometricAvailable_whenKeyInvalidated_shouldDeleteWrapLogEventAndShowBanner() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        keystoreGateway.invalidateKey(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC))

        val cipher = prepareBiometricPrompt()

        assertThat(cipher).isNull()
        assertThat(viewModel.uiState.value.isBiometricKeyInvalidated).isTrue()
        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC) }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.BIOMETRIC_KEY_INVALIDATED) }
        // The alias is rotated by VaultKeyMaintenance at the next unlock, not here.
        assertThat(keystoreGateway.generationCountFor(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC)))
            .isEqualTo(1)
    }

    @Test
    fun onBiometricSuccess_withTheBoundCipher_shouldUnwrapVerifyOpenSessionAndLogBiometricLogin() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        val cipher = prepareBiometricPrompt()!!

        viewModel.onAction(LoginScreenAction.BiometricSuccess(cipher))
        advanceUntilIdle()

        assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
        assertThat(sessionKeyHolder.isLegacySession).isFalse()
        assertThat(sessionKeyHolder.withSessionKey { it.encoded }).isEqualTo(testDek)
        assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.VERIFIED)
        assertThat(viewModel.uiState.value.biometricUnlockCipher).isNull()
        coVerify(exactly = 1) { userDetailsRepository.onAuthSuccess(withBiometric = true) }
        verify(exactly = 1) { vaultKeyMaintenance.scheduleAfterUnlock() }
        val params = capturedLoginSuccessParams()
        assertThat(params[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("biometric")
        assertThat(params[AnalyticsParam.LOGIN_COUNT.paramName]).isEqualTo(FIRST_LOGIN_ORDINAL)
        // A biometric login never writes a backup (the master password is not in hand).
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun onBiometricSuccess_withoutACipher_shouldUnlockNothing() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        prepareBiometricPrompt()

        val outcome = runCatching {
            viewModel.onAction(LoginScreenAction.BiometricSuccess(cipher = null))
            advanceUntilIdle()
        }

        // Debug builds assert; whatever the build, the vault stays locked.
        assertThat(outcome.exceptionOrNull()?.javaClass?.simpleName ?: "none")
            .isAnyOf("DebugFatalException", "none")
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(viewModel.uiState.value.userAuthState).isNotEqualTo(UserAuthState.VERIFIED)
        coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
    }

    @Test
    fun onBiometricSuccess_whenWrapHoldsAnotherDek_shouldTreatAsInvalidatedAndStayLocked() = runTest {
        val otherDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x7f }
        givenBiometricWrap(dek = otherDek)
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        val cipher = prepareBiometricPrompt()!!

        viewModel.onAction(LoginScreenAction.BiometricSuccess(cipher))
        advanceUntilIdle()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(viewModel.uiState.value.isBiometricKeyInvalidated).isTrue()
        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC) }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.BIOMETRIC_KEY_INVALIDATED) }
        coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
    }

    @Test
    fun onBiometricError_shouldDropThePreparedCipher() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        prepareBiometricPrompt()

        viewModel.onAction(LoginScreenAction.BiometricError)

        assertThat(viewModel.uiState.value.canUnlockWithBiometric).isFalse()
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    // endregion

    // region password login

    @Test
    fun onLoginClicked_onEnvelopeVaultWithCorrectPassword_unwrapsDekOpensSessionAndSchedulesMaintenance() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
                advanceUntilIdle()

                assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
                assertThat(sessionKeyHolder.isLegacySession).isFalse()
                verify(exactly = 1) { vaultKeyMaintenance.scheduleAfterUnlock() }
                coVerify(exactly = 1) { userDetailsRepository.onAuthSuccess(withBiometric = false) }
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }

    @Test
    fun onLoginClicked_onEnvelopeVaultWithCorrectPasswordAndAutoBackupEnabled_startsBackupWork() =
        runTest {
            coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns true

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
                advanceUntilIdle()

                val requestSlot = slot<OneTimeWorkRequest>()
                verify(exactly = 1) {
                    workManager.enqueueUniqueWork(
                        eq(CommonConstants.WORKER_NAME_BACKUP_DATA),
                        eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
                        capture(requestSlot),
                    )
                }
                val request = requestSlot.captured
                assertThat(backupPasswordRegistry.take(request.id))
                    .isEqualTo(DEFAULT_PASSWORD.toCharArray())
                assertThat(request.workSpec.input.keyValueMap.values)
                    .doesNotContain(DEFAULT_PASSWORD)
                assertThat(
                    request.workSpec.input.getBoolean(
                        CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION,
                        true,
                    ),
                ).isFalse()
                val updated = expectMostRecentItem()
                assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
        }

    @Test
    fun onLoginClicked_onEnvelopeVaultWithAutoBackupDisabled_doesNotTriggerBackup() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
            advanceUntilIdle()

            verify(exactly = 0) {
                workManager.enqueueUniqueWork(
                    eq(CommonConstants.WORKER_NAME_BACKUP_DATA),
                    any(),
                    any<OneTimeWorkRequest>(),
                )
            }
            coVerify { userDetailsRepository.onAuthSuccess(withBiometric = false) }
            val updated = expectMostRecentItem()
            assertThat(updated.userAuthState).isEqualTo(UserAuthState.VERIFIED)
        }
    }

    @Test
    fun onLoginClicked_onEnvelopeVaultWithIncorrectPassword_updatesStateAndLogsEvent() = runTest {
        val wrongPassword = "wrong_password"
        every {
            envelopeEncryptionManager.unwrapWithPassword(any(), any(), any())
        } returns Result.failure(IllegalArgumentException("bad password"))

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.LoginClicked(wrongPassword))
            advanceUntilIdle()

            verify { analyticsHelper.logEvent(AnalyticsKey.LOGIN_FAILED) }
            val updated = expectMostRecentItem()
            assertThat(updated.userAuthState).isEqualTo(UserAuthState.INCORRECT_PASSWORD_ENTERED)
            assertThat(updated.failedLoginAttempts).isEqualTo(1)
            assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
            coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
        }
    }

    @Test
    fun onLoginClicked_withConsecutiveIncorrectPasswords_incrementsFailedLoginAttempts() = runTest {
        every {
            envelopeEncryptionManager.unwrapWithPassword(any(), any(), any())
        } returns Result.failure(IllegalArgumentException("bad password"))

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.LoginClicked("wrong"))
            advanceUntilIdle()
            assertThat(expectMostRecentItem().failedLoginAttempts).isEqualTo(1)

            viewModel.onAction(LoginScreenAction.LoginClicked("wrong"))
            advanceUntilIdle()

            val updated = expectMostRecentItem()
            assertThat(updated.failedLoginAttempts).isEqualTo(2)
            assertThat(updated.userAuthState).isEqualTo(UserAuthState.INCORRECT_PASSWORD_ENTERED)
            verify(exactly = 2) { analyticsHelper.logEvent(AnalyticsKey.LOGIN_FAILED) }
        }
    }

    @Test
    fun onLoginClicked_onLegacyVaultWithCorrectPassword_runsMigrationAndVerifiesOnSuccess() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        coEvery { userDetailsRepository.checkPassword(DEFAULT_PASSWORD) } returns true
        coEvery {
            legacyVaultMigrator.migrate(any(), isNull(), MigrationTrigger.LOGIN)
        } returns MigrationOutcome.Success(dek = testDek.copyOf())

        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        coVerify(exactly = 1) { userDetailsRepository.checkPassword(DEFAULT_PASSWORD) }
        coVerify(exactly = 1) {
            legacyVaultMigrator.migrate(
                match { it.contentEquals(DEFAULT_PASSWORD.toCharArray()) },
                null,
                MigrationTrigger.LOGIN,
            )
        }
        verify(exactly = 1) { vaultKeyMaintenance.scheduleAfterUnlock() }
        assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.VERIFIED)
    }

    @Test
    fun onMigrationRetryAndContinueLegacy_whenMigrationFailsWithKeystoreTransient_logsEventsAndOpensLegacySession() =
        runTest {
            coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
            coEvery { userDetailsRepository.checkPassword(DEFAULT_PASSWORD) } returns true
            coEvery {
                legacyVaultMigrator.migrate(any(), any(), any())
            } returns MigrationOutcome.Failure(
                stage = MigrationStage.DECRYPT_ROW,
                reason = MigrationFailureReason.KEYSTORE_TRANSIENT,
                attempt = 2,
                trigger = MigrationTrigger.LOGIN,
            )

            viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
            advanceUntilIdle()

            val failure = viewModel.uiState.value.migrationFailure
            assertThat(failure).isNotNull()
            assertThat(failure!!.canContinueLegacy).isTrue()

            viewModel.onAction(LoginScreenAction.OnMigrationFailureShown(MigrationFailureReason.KEYSTORE_TRANSIENT))
            assertThat(capturedParams(AnalyticsKey.VAULT_MIGRATION_FAILURE_SHOW)[AnalyticsParam.REASON.paramName])
                .isEqualTo("keystore_transient")

            viewModel.onAction(LoginScreenAction.OnMigrationRetryClicked)
            advanceUntilIdle()
            assertThat(capturedParams(AnalyticsKey.VAULT_MIGRATION_RETRY_CLICK)[AnalyticsParam.REASON.paramName])
                .isEqualTo("keystore_transient")
            coVerify(exactly = 2) { legacyVaultMigrator.migrate(any(), null, MigrationTrigger.LOGIN) }

            viewModel.onAction(LoginScreenAction.OnMigrationContinueLegacyClicked)
            advanceUntilIdle()
            assertThat(
                capturedParams(AnalyticsKey.VAULT_MIGRATION_CONTINUE_LEGACY_CLICK)[AnalyticsParam.REASON.paramName],
            ).isEqualTo("keystore_transient")
            assertThat(
                capturedParams(AnalyticsKey.VAULT_LEGACY_SESSION_START)[AnalyticsParam.ATTEMPT.paramName],
            ).isEqualTo(2)
            assertThat(sessionKeyHolder.isLegacySession).isTrue()
            val loginParams = capturedLoginSuccessParams()
            assertThat(loginParams[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("legacy_session")
            assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.VERIFIED)
        }

    // endregion

    // region forgot password: legacy gate (design §6.4.1)

    @Test
    fun forgotPassword_onLegacyVault_shouldStartTheLegacyGateAndLogTheClick() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy

        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.FORGOT_PASSWORD_CLICK) }
        verify(exactly = 0) { analyticsHelper.logEvent(eq(AnalyticsKey.FORGOT_PASSWORD_CLICK), any()) }
        assertThat(viewModel.uiState.value.forgotPasswordStep).isEqualTo(ForgotPasswordStep.LegacyAuthentication)
        verify(exactly = 0) { analyticsHelper.logEvent(AnalyticsKey.RECOVERY_NOT_SET_UP_SHOW) }
    }

    @Test
    fun legacyResetAuthenticated_shouldAskForTheNewPassword() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnLegacyResetAuthenticated)

        assertThat(viewModel.uiState.value.forgotPasswordStep).isEqualTo(ForgotPasswordStep.NewPassword)
    }

    @Test
    fun onResetPassword_onLegacyVault_runsForgotPasswordMigrationDirectlyWithoutUpdatingLegacyHash() =
        runTest {
            coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
            coEvery {
                legacyVaultMigrator.migrate(any(), eq("new hint"), MigrationTrigger.FORGOT_PASSWORD)
            } returns MigrationOutcome.Success(dek = testDek.copyOf())

            viewModel.onAction(
                LoginScreenAction.OnResetPassword(
                    newPassword = "NewPassword@@123",
                    hint = "new hint",
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { userDetailsRepository.updatePasswordAndHint(any(), any()) }
            coVerify(exactly = 1) {
                legacyVaultMigrator.migrate(
                    match { it.contentEquals("NewPassword@@123".toCharArray()) },
                    "new hint",
                    MigrationTrigger.FORGOT_PASSWORD,
                )
            }
            assertLoggedFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
            val loginParams = capturedLoginSuccessParams()
            assertThat(loginParams[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("password_reset")
            assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.VERIFIED)
            assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        }

    @Test
    fun deviceSecurityRequiredDismissed_shouldCloseTheLegacyGateAndLogEvent() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnDeviceSecurityRequiredDismissClicked)

        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        assertLoggedFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
    }

    @Test
    fun onDeviceSecurityRequiredOpenSettingsClicked_pausesSessionManagerClosesTheGateAndLogsEvent() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked)

        verify { activeSessionManager.setPaused(true) }
        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        assertLoggedFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
    }

    @Test
    fun onDeviceSecurityRequiredDialogShown_logsEvent() = runTest {
        viewModel.onAction(LoginScreenAction.OnDeviceSecurityRequiredDialogShown)
        assertLoggedFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
    }

    @Test
    fun onDeviceSecurityRequiredOpenSettingsFailed_logsEvent() = runTest {
        viewModel.onAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed)
        assertLoggedFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
    }

    @Test
    fun onUpdatePasswordDialogShown_logsEvent() = runTest {
        viewModel.onAction(LoginScreenAction.OnUpdatePasswordDialogShown)
        assertLoggedFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
    }

    @Test
    fun forgotPasswordAuthCancelled_shouldCloseTheStep() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnForgotPasswordAuthCancelled)

        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
    }

    // endregion

    // region forgot password: envelope recovery (design §6.7)

    @Test
    fun forgotPassword_onEnvelopeVaultWithoutDeviceWrap_shouldShowRecoveryNotSetUp() = runTest {
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isRecoveryNotSetUp).isTrue()
        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.RECOVERY_NOT_SET_UP_SHOW) }
        verify(exactly = 0) { analyticsHelper.logEvent(AnalyticsKey.DEVICE_KEY_INVALIDATED) }

        viewModel.onAction(LoginScreenAction.OnRecoveryNotSetUpDismissed)

        assertThat(viewModel.uiState.value.isRecoveryNotSetUp).isFalse()
    }

    @Test
    fun forgotPassword_whenDeviceKeyInvalidated_shouldDeleteWrapLogEventAndShowRecoveryNotSetUp() = runTest {
        givenDeviceWrap()
        keystoreGateway.invalidateKey(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE))

        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.DEVICE) }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.DEVICE_KEY_INVALIDATED) }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.RECOVERY_NOT_SET_UP_SHOW) }
        assertThat(viewModel.uiState.value.isRecoveryNotSetUp).isTrue()
        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
    }

    @Test
    fun forgotPassword_withHealthyDeviceKey_shouldOpenThePromptWithTheCipher() = runTest {
        givenDeviceWrap()

        val step = startDeviceRecovery()

        assertThat(step.cipher).isNotNull()
        assertThat(viewModel.uiState.value.isRecoveryNotSetUp).isFalse()
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.FORGOT_PASSWORD_CLICK) }
    }

    @Test
    fun forgotPassword_withTimeBoundDeviceKeyOutsideItsWindow_shouldOpenThePromptWithoutCipher() = runTest {
        givenDeviceWrap()
        keystoreGateway.setRequireAuthOnInit(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE))

        val step = startDeviceRecovery()

        assertThat(step.cipher).isNull()
        coVerify(exactly = 0) { vaultRepository.deleteWrap(any()) }
    }

    @Test
    fun recoveryAuthenticated_withTheBoundCipher_shouldHoldTheDekAndAskForTheNewPassword() = runTest {
        givenDeviceWrap()
        val step = startDeviceRecovery()

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.forgotPasswordStep).isEqualTo(ForgotPasswordStep.NewPassword)
        // Not the session yet: the new wrap must be on disk first.
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 0) { analyticsHelper.logEvent(eq(AnalyticsKey.VAULT_RECOVERY_FAILURE), any()) }
    }

    @Test
    fun onResetPassword_afterRecovery_shouldRewrapWithHintOpenSessionAndLogRecoveryAndPasswordReset() = runTest {
        givenDeviceWrap()
        var rewrappedPassword: CharArray? = null
        var rewrappedDek: ByteArray? = null
        var rewrappedHint: String? = null
        // Copied at call time: the ViewModel zeroes the DEK the moment the wrap is on disk.
        coEvery { vaultRepository.rewrapPassword(any(), any(), any()) } coAnswers {
            rewrappedPassword = firstArg<CharArray>().copyOf()
            rewrappedDek = secondArg<ByteArray>().copyOf()
            rewrappedHint = thirdArg()
        }
        val step = startDeviceRecovery()
        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnResetPassword(newPassword = "NewPassword@@123", hint = "new hint"))
        advanceUntilIdle()

        assertThat(rewrappedPassword).isEqualTo("NewPassword@@123".toCharArray())
        assertThat(rewrappedDek).isEqualTo(testDek)
        assertThat(rewrappedHint).isEqualTo("new hint")
        coVerify(exactly = 0) { userDetailsRepository.updatePasswordAndHint(any(), any()) }
        assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
        assertThat(sessionKeyHolder.withSessionKey { it.encoded }).isEqualTo(testDek)
        verify(exactly = 1) { vaultKeyMaintenance.scheduleAfterUnlock() }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.VAULT_RECOVERY_SUCCESS) }
        assertLoggedFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        coVerify(exactly = 1) { userDetailsRepository.onAuthSuccess(withBiometric = false) }
        assertThat(capturedLoginSuccessParams()[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("password_reset")
        assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.VERIFIED)
        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
    }

    @Test
    fun recoveryAuthenticated_withoutCipher_shouldUnwrapInsideTheTimeBoundWindow() = runTest {
        givenDeviceWrap()
        val deviceAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE)
        keystoreGateway.setRequireAuthOnInit(deviceAlias)
        val step = startDeviceRecovery()
        assertThat(step.cipher).isNull()
        // The prompt just succeeded: the key is inside its 5 s window.
        keystoreGateway.setRequireAuthOnInit(deviceAlias, required = false)

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(cipher = null))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.forgotPasswordStep).isEqualTo(ForgotPasswordStep.NewPassword)
        assertThat(viewModel.uiState.value.recoveryFailure).isNull()
    }

    @Test
    fun recoveryAuthenticated_whenTheWindowIsMissed_shouldRepromptOnceThenFailWithNotAuthenticated() = runTest {
        givenDeviceWrap()
        keystoreGateway.setRequireAuthOnInit(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE))
        val firstStep = startDeviceRecovery()

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(cipher = null))
        advanceUntilIdle()

        val secondStep = viewModel.uiState.value.forgotPasswordStep
        assertThat(secondStep).isInstanceOf(ForgotPasswordStep.DeviceAuthentication::class.java)
        assertThat(secondStep).isNotSameInstanceAs(firstStep)
        assertThat((secondStep as ForgotPasswordStep.DeviceAuthentication).cipher).isNull()

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(cipher = null))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        assertThat(viewModel.uiState.value.recoveryFailure).isEqualTo(RecoveryFailureReason.NOT_AUTHENTICATED)
        val params = capturedParams(AnalyticsKey.VAULT_RECOVERY_FAILURE)
        assertThat(params[AnalyticsParam.REASON.paramName]).isEqualTo("not_authenticated")
        assertThat(params[AnalyticsParam.EXCEPTION.paramName]).isEqualTo("none")
        // The key is healthy; the wrap stays for the next attempt.
        coVerify(exactly = 0) { vaultRepository.deleteWrap(any()) }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun recoveryAuthenticated_whenWrapHoldsAnotherDek_shouldDeleteWrapFailWithVerifierAndWriteNothing() = runTest {
        val otherDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x7f }
        givenDeviceWrap(dek = otherDek)
        val step = startDeviceRecovery()

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recoveryFailure).isEqualTo(RecoveryFailureReason.VERIFIER)
        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.DEVICE) }
        coVerify(exactly = 0) { vaultRepository.rewrapPassword(any(), any(), any()) }
        verify(exactly = 0) { analyticsHelper.logEvent(AnalyticsKey.DEVICE_KEY_INVALIDATED) }
        assertThat(capturedParams(AnalyticsKey.VAULT_RECOVERY_FAILURE)[AnalyticsParam.REASON.paramName])
            .isEqualTo("verifier")
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun recoveryAuthenticated_whenTheUnwrapFails_shouldInvalidateTheDeviceKeyAndFailWithKeystore() = runTest {
        val blob = givenDeviceWrap()
        val tampered = Base64.getDecoder().decode(blob).also { it[10] = (it[10].toInt() xor 0x55).toByte() }
        coEvery { vaultRepository.getVaultState() } returns defaultEnvelopeState.copy(
            wrappedDekDevice = Base64.getEncoder().encodeToString(tampered),
        )
        val step = startDeviceRecovery()

        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recoveryFailure).isEqualTo(RecoveryFailureReason.KEYSTORE)
        coVerify(exactly = 1) { vaultRepository.deleteWrap(WrapKeySpec.DEVICE) }
        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.DEVICE_KEY_INVALIDATED) }
        val params = capturedParams(AnalyticsKey.VAULT_RECOVERY_FAILURE)
        assertThat(params[AnalyticsParam.REASON.paramName]).isEqualTo("keystore")
        assertThat(params[AnalyticsParam.EXCEPTION.paramName]).isEqualTo("BadPaddingException")
        coVerify(exactly = 0) { vaultRepository.rewrapPassword(any(), any(), any()) }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun onResetPassword_whenTheRewrapCannotBeSaved_shouldFailWithStorageAndStayLocked() = runTest {
        givenDeviceWrap()
        coEvery { vaultRepository.rewrapPassword(any(), any(), any()) } throws IllegalStateException("disk full")
        val step = startDeviceRecovery()
        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnResetPassword(newPassword = "NewPassword@@123", hint = "new hint"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recoveryFailure).isEqualTo(RecoveryFailureReason.STORAGE)
        val params = capturedParams(AnalyticsKey.VAULT_RECOVERY_FAILURE)
        assertThat(params[AnalyticsParam.REASON.paramName]).isEqualTo("storage")
        assertThat(params[AnalyticsParam.EXCEPTION.paramName]).isEqualTo("IllegalStateException")
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 0) { analyticsHelper.logEvent(AnalyticsKey.VAULT_RECOVERY_SUCCESS) }
        verify(exactly = 0) { analyticsHelper.logEvent(eq(AnalyticsKey.LOGIN_SUCCESS), any()) }
    }

    @Test
    fun updatePasswordDismissed_afterRecovery_shouldCloseTheDialogLogEventAndKeepTheVaultLocked() = runTest {
        givenDeviceWrap()
        val step = startDeviceRecovery()
        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnUpdatePasswordDismissClicked)

        assertThat(viewModel.uiState.value.forgotPasswordStep).isNull()
        assertLoggedFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        coVerify(exactly = 0) { vaultRepository.rewrapPassword(any(), any(), any()) }
    }

    @Test
    fun recoveryFailureBanner_shouldBeClearedByTheNextForgotPasswordTap() = runTest {
        val otherDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x7f }
        givenDeviceWrap(dek = otherDek)
        val step = startDeviceRecovery()
        viewModel.onAction(LoginScreenAction.OnRecoveryAuthenticated(step.cipher))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.recoveryFailure).isEqualTo(RecoveryFailureReason.VERIFIER)

        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)

        assertThat(viewModel.uiState.value.recoveryFailure).isNull()
    }

    // endregion

    // region erase (design §6.11)

    @Test
    fun eraseVaultDialog_fromRecoveryNotSetUp_shouldLogTheTriadWithItsSource() = runTest {
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isRecoveryNotSetUp).isTrue()

        viewModel.onAction(LoginScreenAction.OnEraseVaultDialogShown)
        viewModel.onAction(LoginScreenAction.OnEraseVaultCancelClicked)
        viewModel.onAction(LoginScreenAction.OnEraseVaultConfirmClicked)
        advanceUntilIdle()

        assertThat(capturedParams(AnalyticsKey.ERASE_VAULT_DIALOG_SHOW)[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.RECOVERY_NOT_SET_UP.value)
        assertThat(capturedParams(AnalyticsKey.ERASE_VAULT_DIALOG_CANCEL_CLICK)[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.RECOVERY_NOT_SET_UP.value)
        assertThat(capturedParams(AnalyticsKey.ERASE_VAULT_DIALOG_CONFIRM_CLICK)[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.RECOVERY_NOT_SET_UP.value)
    }

    @Test
    fun eraseVaultConfirmed_shouldEraseTheVaultAndLeaveForSignup() = runTest {
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnEraseVaultConfirmClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultResetUseCase.eraseVault() }
        assertThat(viewModel.uiState.value.isVaultErased).isTrue()
    }

    @Test
    fun eraseVaultConfirmed_twiceWhileInFlight_shouldEraseOnce() = runTest {
        viewModel.onAction(LoginScreenAction.ForgotPasswordClicked)
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.OnEraseVaultConfirmClicked)
        viewModel.onAction(LoginScreenAction.OnEraseVaultConfirmClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultResetUseCase.eraseVault() }
    }

    @Test
    fun eraseVaultDialog_fromLegacyKeyLost_shouldLogTheSourceAsLegacyKeyLost() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        coEvery { userDetailsRepository.checkPassword(DEFAULT_PASSWORD) } returns true
        keystoreGateway.legacyKey = null
        coEvery {
            legacyVaultMigrator.migrate(any(), any(), any())
        } returns MigrationOutcome.Failure(
            stage = MigrationStage.LOAD_LEGACY_KEY,
            reason = MigrationFailureReason.LEGACY_KEY_LOST,
            trigger = MigrationTrigger.LOGIN,
        )
        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.userAuthState).isEqualTo(UserAuthState.LEGACY_KEY_LOST)

        viewModel.onAction(LoginScreenAction.OnEraseVaultDialogShown)
        viewModel.onAction(LoginScreenAction.OnEraseVaultConfirmClicked)
        advanceUntilIdle()

        assertThat(capturedParams(AnalyticsKey.ERASE_VAULT_DIALOG_SHOW)[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.LEGACY_KEY_LOST.value)
        coVerify(exactly = 1) { vaultResetUseCase.eraseVault() }
        assertThat(viewModel.uiState.value.isVaultErased).isTrue()
    }

    // endregion

    @Test
    fun onShowHintClicked_fetchesAndDisplaysHint() = runTest {
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
    fun onShowHintClicked_withNullHintKeepsHintEmpty() = runTest {
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
    fun passwordLogin_shouldLogLoginSuccessWithPasswordMethodAndOrdinal() = runTest {
        coEvery { userDetailsRepository.onAuthSuccess(withBiometric = false) } returns 7

        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        val params = capturedLoginSuccessParams()
        assertThat(params[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("password")
        assertThat(params[AnalyticsParam.LOGIN_COUNT.paramName]).isEqualTo(7)
    }

    @Test
    fun loginSuccess_shouldCarryDeviceSecurityParamsFromTheSnapshot() = runTest {
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = false,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = true,
        )

        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        val params = capturedLoginSuccessParams()
        assertThat(params[AnalyticsParam.DEVICE_SECURE.paramName]).isEqualTo(false)
        assertThat(params[AnalyticsParam.BIOMETRIC_CLASS.paramName]).isEqualTo("none_enrolled")
        assertThat(params[AnalyticsParam.STRONGBOX.paramName]).isEqualTo(true)
    }

    @Test
    fun loginSuccess_whenDeviceSecurityReadThrows_shouldStillVerifyAndLogWithoutDeviceParams() =
        runTest {
            every { deviceSecurityStatus.snapshot() } throws IllegalStateException("keyguard gone")

            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
                advanceUntilIdle()

                assertThat(expectMostRecentItem().userAuthState).isEqualTo(UserAuthState.VERIFIED)
            }
            val params = capturedLoginSuccessParams()
            assertThat(params[AnalyticsParam.LOGIN_METHOD.paramName]).isEqualTo("password")
            assertThat(params).doesNotContainKey(AnalyticsParam.DEVICE_SECURE.paramName)
            assertThat(params).doesNotContainKey(AnalyticsParam.BIOMETRIC_CLASS.paramName)
            assertThat(params).doesNotContainKey(AnalyticsParam.STRONGBOX.paramName)
        }

    @Test
    fun passwordLogin_withAutoBackup_shouldOpenTheSessionBeforeEnqueuingTheWorker() = runTest {
        coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns true
        var sessionOpenAtEnqueue = false
        every { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } answers {
            sessionOpenAtEnqueue = sessionKeyHolder.isUnlocked.value
            mockk()
        }

        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        assertThat(sessionOpenAtEnqueue).isTrue()
    }

    @Test
    fun passwordLogin_onLegacyVaultWhenKeyIsLost_shouldReportLostKeyAndNeverVerify() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        coEvery { userDetailsRepository.checkPassword(DEFAULT_PASSWORD) } returns true
        coEvery { settingsDataStore.getAutoBackupAfterPasswordLogin() } returns true
        keystoreGateway.legacyKey = null
        coEvery {
            legacyVaultMigrator.migrate(any(), any(), any())
        } returns MigrationOutcome.Failure(
            stage = MigrationStage.LOAD_LEGACY_KEY,
            reason = MigrationFailureReason.LEGACY_KEY_LOST,
            trigger = MigrationTrigger.LOGIN,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
            advanceUntilIdle()

            assertThat(expectMostRecentItem().userAuthState).isEqualTo(UserAuthState.LEGACY_KEY_LOST)
        }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(keystoreGateway.legacyVaultKey()).isNull()
        coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(any()) }
        verify(exactly = 0) { analyticsHelper.logEvent(eq(AnalyticsKey.LOGIN_SUCCESS), any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun loginClicked_whenCalledTwiceWhileInFlightOrAfterVerified_unwrapsOnlyOnce() = runTest {
        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()
        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        verify(exactly = 1) { envelopeEncryptionManager.unwrapWithPassword(any(), any(), any()) }
        coVerify(exactly = 1) { userDetailsRepository.onAuthSuccess(false) }
    }

    @Test
    fun biometricSuccess_arrivingAfterAPasswordLogin_shouldNotUnlockTwice() = runTest {
        givenBiometricWrap()
        coEvery { userDetailsRepository.shouldStartBiometricAuthFlow() } returns true
        val cipher = prepareBiometricPrompt()!!
        viewModel.onAction(LoginScreenAction.LoginClicked(DEFAULT_PASSWORD))
        advanceUntilIdle()

        viewModel.onAction(LoginScreenAction.BiometricSuccess(cipher))
        advanceUntilIdle()

        coVerify(exactly = 1) { userDetailsRepository.onAuthSuccess(any()) }
        coVerify(exactly = 0) { userDetailsRepository.onAuthSuccess(withBiometric = true) }
    }

    /** Runs the captured param block of the single LOGIN_SUCCESS event and returns what it set. */
    private fun capturedLoginSuccessParams(): Map<String, Any> = capturedParams(AnalyticsKey.LOGIN_SUCCESS)

    /** Runs the captured param block of the single [key] event and returns what it set. */
    private fun capturedParams(key: AnalyticsKey): Map<String, Any> {
        val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
        verify(exactly = 1) { analyticsHelper.logEvent(eq(key), capture(slot)) }
        return AnalyticsParamsBuilder().apply(slot.captured).params
    }

    /** The dialog triads are shared with Settings, so every login-screen emission must say so. */
    private fun assertLoggedFromLoginScreen(key: AnalyticsKey) {
        assertThat(capturedParams(key)[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.LOGIN.value)
    }

    companion object {
        private const val FIRST_LOGIN_ORDINAL = 1
        private const val DEFAULT_PASSWORD = "password"
        private val SECURE_WITH_STRONG_BIOMETRIC = DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.STRONG,
            hasStrongBox = false,
        )
    }
}
