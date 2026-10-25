package com.andryoga.safebox.ui.login

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.di.IsDebug
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.andryoga.safebox.security.envelope.VaultResetUseCase
import com.andryoga.safebox.security.envelope.migration.LegacyVaultMigrator
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationOutcome
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.security.envelope.migration.VaultMigrationProgress
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.SessionLockReasonStore
import com.andryoga.safebox.worker.BackupDataWorker
import com.andryoga.safebox.worker.BackupPasswordRegistry
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userDetailsRepository: UserDetailsRepository,
    private val vaultRepository: VaultRepository,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val legacyVaultMigrator: Lazy<LegacyVaultMigrator>,
    private val vaultMigrationProgress: VaultMigrationProgress,
    private val vaultKeyMaintenance: VaultKeyMaintenance,
    private val vaultResetUseCase: Lazy<VaultResetUseCase>,
    private val workManager: Lazy<WorkManager>,
    private val backupPasswordRegistry: BackupPasswordRegistry,
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val activeSessionManager: ActiveSessionManager,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val sessionKeyHolder: SessionKeyHolder,
    private val keystoreGateway: KeystoreGateway,
    private val dispatchersProvider: DispatchersProvider,
    sessionLockReasonStore: SessionLockReasonStore,
    @param:IsDebug private val isDebug: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        // One ViewModel per arrival on Login: consumed here, rotation keeps the banner, the next
        // arrival does not see it.
        LoginUiState(lockReason = sessionLockReasonStore.consume()),
    )
    val uiState = _uiState.asStateFlow()

    // Held only in RAM while the migration failure card is showing so Try again and Continue for
    // now do not force a second password entry or device-credential prompt.
    // TODO(D3): legacy vault (crypto_version = 1) only
    private var pendingMigrationPassword: String? = null
    private var pendingMigrationHint: String? = null
    private var pendingMigrationTrigger: MigrationTrigger = MigrationTrigger.LOGIN

    // The DEK recovered through wrapped_dek_device, held between the device prompt and the new
    // password; never in the session until the new wrap is on disk (design §6.7).
    private var pendingRecoveryDek: ByteArray? = null

    // API 29 only: one automatic re-prompt when the time-bound key refuses the unwrap (design §6.7).
    private var recoveryRepromptUsed = false

    init {
        if (isDebug) {
            _uiState.update { it.copy(defaultPassword = "Qwerty@@135") }
        }
        vaultMigrationProgress.state.onEach { progress ->
            _uiState.update { it.copy(migrationProgress = progress) }
        }.launchIn(viewModelScope)
        viewModelScope.launch {
            val state = vaultRepository.getVaultState()
            if (state is VaultState.Legacy) {
                _uiState.update { it.copy(isLegacyVault = true) }
            }
        }
    }

    fun onAction(action: LoginScreenAction) {
        when (action) {
            is LoginScreenAction.LoginClicked -> onLoginClicked(action.password)
            LoginScreenAction.ShowHintClicked -> getHintFromDb()
            is LoginScreenAction.BiometricSuccess -> onBiometricSuccess(action.cipher)
            LoginScreenAction.BiometricAvailable -> onBiometricAvailable()
            LoginScreenAction.BiometricError -> onBiometricError()
            is LoginScreenAction.OnResetPassword -> onResetPassword(action.newPassword, action.hint)
            LoginScreenAction.ForgotPasswordClicked -> onForgotPasswordClicked()
            LoginScreenAction.OnLegacyResetAuthenticated -> {
                _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.NewPassword) }
            }

            is LoginScreenAction.OnRecoveryAuthenticated -> onRecoveryAuthenticated(action.cipher)
            LoginScreenAction.OnForgotPasswordAuthCancelled -> {
                _uiState.update { it.copy(forgotPasswordStep = null) }
            }

            LoginScreenAction.OnRecoveryNotSetUpDismissed -> {
                _uiState.update { it.copy(isRecoveryNotSetUp = false) }
            }

            LoginScreenAction.OnDeviceSecurityRequiredDialogShown -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked -> {
                activeSessionManager.setPaused(true)
                _uiState.update { it.copy(forgotPasswordStep = null) }
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDismissClicked -> {
                _uiState.update { it.copy(forgotPasswordStep = null) }
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }

            LoginScreenAction.OnUpdatePasswordDialogShown -> {
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            LoginScreenAction.OnUpdatePasswordDismissClicked -> {
                // Cancelling the dialog drops the recovered DEK; the wraps on disk are untouched.
                clearPendingRecoveryDek()
                _uiState.update { it.copy(forgotPasswordStep = null) }
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
            }

            LoginScreenAction.OnEraseVaultDialogShown -> logFromEraseState(AnalyticsKey.ERASE_VAULT_DIALOG_SHOW)
            LoginScreenAction.OnEraseVaultCancelClicked -> logFromEraseState(AnalyticsKey.ERASE_VAULT_DIALOG_CANCEL_CLICK)
            LoginScreenAction.OnEraseVaultConfirmClicked -> onEraseVaultConfirmClicked()
            is LoginScreenAction.OnMigrationFailureShown -> onMigrationFailureShown(action.reason)
            LoginScreenAction.OnMigrationRetryClicked -> onMigrationRetryClicked()
            LoginScreenAction.OnMigrationContinueLegacyClicked -> onMigrationContinueLegacyClicked()
        }
    }

    /** The same dialog events are raised from Settings; `source` keeps the two apart in Firebase. */
    private fun logFromLoginScreen(key: AnalyticsKey) {
        analyticsHelper.logEvent(key) {
            param(AnalyticsParam.SOURCE, AnalyticsSource.LOGIN.value)
        }
    }

    /** The erase dialog opens from two login-screen states; `source` says which one offered it. */
    private fun logFromEraseState(key: AnalyticsKey) {
        val source = if (_uiState.value.userAuthState == UserAuthState.LEGACY_KEY_LOST) {
            AnalyticsSource.LEGACY_KEY_LOST
        } else {
            AnalyticsSource.RECOVERY_NOT_SET_UP
        }
        analyticsHelper.logEvent(key) {
            param(AnalyticsParam.SOURCE, source.value)
        }
    }

    private fun onForgotPasswordClicked() {
        analyticsHelper.logEvent(AnalyticsKey.FORGOT_PASSWORD_CLICK)
        recoveryRepromptUsed = false
        _uiState.update { it.copy(recoveryFailure = null) }
        viewModelScope.launch {
            when (val state = vaultRepository.getVaultState()) {
                is VaultState.Legacy -> {
                    // Nothing is tied to a credential yet: today's gate, then the migration (design §6.4.1).
                    _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.LegacyAuthentication) }
                }

                is VaultState.Envelope -> startDeviceRecovery(state)
                VaultState.CorruptedEnvelope -> onCorruptedEnvelopeState()
            }
        }
    }

    /** Design §6.7: a usable device wrap opens the prompt; anything else is the recovery-not-set-up state. */
    private suspend fun startDeviceRecovery(state: VaultState.Envelope) {
        if (state.wrappedDekDevice == null) {
            showRecoveryNotSetUp()
            return
        }
        val probe = withContext(dispatchersProvider.default) {
            probeWrapKey(WrapKeySpec.DEVICE, state.deviceKeyAliasSuffix)
        }
        when (probe) {
            is WrapKeyProbe.Ready -> {
                _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.DeviceAuthentication(probe.cipher)) }
            }

            WrapKeyProbe.AwaitingAuthentication -> {
                _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.DeviceAuthentication(cipher = null)) }
            }

            WrapKeyProbe.Invalidated -> {
                onDeviceKeyInvalidated()
                showRecoveryNotSetUp()
            }
        }
    }

    private fun showRecoveryNotSetUp() {
        Timber.i("forgot password on an envelope vault without a usable device wrap")
        analyticsHelper.logEvent(AnalyticsKey.RECOVERY_NOT_SET_UP_SHOW)
        _uiState.update { it.copy(isRecoveryNotSetUp = true, forgotPasswordStep = null) }
    }

    private suspend fun onDeviceKeyInvalidated() {
        Timber.w("device wrap key invalidated; deleting the device wrap")
        vaultRepository.deleteWrap(WrapKeySpec.DEVICE)
        analyticsHelper.logEvent(AnalyticsKey.DEVICE_KEY_INVALIDATED)
    }

    private fun onRecoveryAuthenticated(cipher: Cipher?) = launchUnlock {
        _uiState.update { it.copy(forgotPasswordStep = null) }
        val state = vaultRepository.getVaultState() as? VaultState.Envelope
        val wrappedDevice = state?.wrappedDekDevice
        if (wrappedDevice == null) {
            // The wrap was there when the prompt opened; losing it in between is a defect.
            Utils.crashInDebugBuild("device prompt succeeded but the device wrap is gone")
            showRecoveryNotSetUp()
            return@launchUnlock
        }
        val outcome = withContext(dispatchersProvider.default) {
            recoverDek(cipher, state, wrappedDevice)
        }
        when (outcome) {
            is RecoveryOutcome.Recovered -> {
                clearPendingRecoveryDek()
                pendingRecoveryDek = outcome.dek
                _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.NewPassword) }
            }

            RecoveryOutcome.NotAuthenticated -> {
                if (!recoveryRepromptUsed) {
                    recoveryRepromptUsed = true
                    Timber.i("time-bound device key refused the unwrap; prompting again")
                    _uiState.update { it.copy(forgotPasswordStep = ForgotPasswordStep.DeviceAuthentication(cipher = null)) }
                } else {
                    onRecoveryFailed(RecoveryFailureReason.NOT_AUTHENTICATED, cause = null)
                }
            }

            RecoveryOutcome.WrongDek -> {
                // The key worked but the bytes are not this vault's DEK: the wrap can never recover
                // anything, so it goes and the next unlock recreates it from the real DEK (§6.5).
                vaultRepository.deleteWrap(WrapKeySpec.DEVICE)
                onRecoveryFailed(RecoveryFailureReason.VERIFIER, cause = null)
            }

            is RecoveryOutcome.KeystoreFailure -> {
                // A doFinal failure right after a successful prompt is an invalidation (design §2.2).
                onDeviceKeyInvalidated()
                onRecoveryFailed(RecoveryFailureReason.KEYSTORE, cause = outcome.cause)
            }
        }
    }

    /**
     * Unwraps `wrapped_dek_device` with [cipher], or with a cipher initialised now when the prompt
     * was not crypto-bound (API 29: inside the time-bound key's window), and checks it against
     * `dek_verifier`. Runs off the main thread: the RSA private operation takes up to ~300 ms in
     * StrongBox.
     */
    private fun recoverDek(
        cipher: Cipher?,
        state: VaultState.Envelope,
        wrappedDevice: String,
    ): RecoveryOutcome {
        val dek = try {
            val authorisedCipher = cipher
                ?: envelopeEncryptionManager.decryptCipherFor(WrapKeySpec.DEVICE, state.deviceKeyAliasSuffix)
            envelopeEncryptionManager.unwrapWithCipher(authorisedCipher, wrappedDevice)
        } catch (_: UserNotAuthenticatedException) {
            return RecoveryOutcome.NotAuthenticated
        } catch (e: Exception) {
            flagIfOutsideCryptoApi("device wrap unwrap failed outside the crypto contract", e)
            return RecoveryOutcome.KeystoreFailure(e)
        }
        if (!envelopeEncryptionManager.verify(dek, state.dekVerifier)) {
            dek.fill(0)
            return RecoveryOutcome.WrongDek
        }
        return RecoveryOutcome.Recovered(dek)
    }

    private fun onRecoveryFailed(reason: RecoveryFailureReason, cause: Throwable?) {
        Timber.w(cause, "vault recovery failed: reason=%s", reason.wireName)
        analyticsHelper.logEvent(AnalyticsKey.VAULT_RECOVERY_FAILURE) {
            param(AnalyticsParam.REASON, reason.wireName)
            param(AnalyticsParam.EXCEPTION, cause?.javaClass?.simpleName ?: NO_EXCEPTION)
        }
        _uiState.update { it.copy(recoveryFailure = reason, forgotPasswordStep = null) }
    }

    private fun onResetPassword(newPassword: String, hint: String) = launchUnlock {
        Timber.i("resetting master password and hint from login screen")
        logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        _uiState.update { it.copy(forgotPasswordStep = null) }
        when (vaultRepository.getVaultState()) {
            is VaultState.Legacy -> {
                // The legacy hash is not rewritten; the new password goes straight into the
                // migration so a failed attempt leaves the original credential intact.
                runLegacyMigration(
                    password = newPassword,
                    newHint = hint,
                    trigger = MigrationTrigger.FORGOT_PASSWORD,
                )
            }

            is VaultState.Envelope -> completeRecovery(newPassword, hint)
            VaultState.CorruptedEnvelope -> onCorruptedEnvelopeState()
        }
    }

    /** Design §6.7 tail: the recovered DEK is re-wrapped under the new password, then becomes the session. */
    private suspend fun completeRecovery(newPassword: String, hint: String) {
        val dek = pendingRecoveryDek
        pendingRecoveryDek = null
        if (dek == null) {
            // The dialog is only reachable through a verified recovery; reaching it without the DEK is a defect.
            Utils.crashInDebugBuild("password reset on an envelope vault without a recovered DEK")
            return
        }
        try {
            try {
                vaultRepository.rewrapPassword(newPassword.toCharArray(), dek, hint)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                onRecoveryFailed(RecoveryFailureReason.STORAGE, cause = e)
                return
            }
            sessionKeyHolder.setSessionKey(dek)
        } finally {
            dek.fill(0)
        }
        Timber.i("master password reset through the device wrap")
        vaultKeyMaintenance.scheduleAfterUnlock()
        analyticsHelper.logEvent(AnalyticsKey.VAULT_RECOVERY_SUCCESS)
        onAuthSuccess(LoginMethod.PASSWORD_RESET)
    }

    private fun clearPendingRecoveryDek() {
        pendingRecoveryDek?.fill(0)
        pendingRecoveryDek = null
    }

    override fun onCleared() {
        clearPendingRecoveryDek()
    }

    private fun onBiometricAvailable() {
        Timber.i("biometric capability is available")
        viewModelScope.launch {
            val state = vaultRepository.getVaultState()
            if (state is VaultState.Legacy) {
                // Biometric login is suppressed while crypto_version = 1.
                _uiState.update { it.copy(isLegacyVault = true, biometricUnlockCipher = null) }
                return@launch
            }
            if (state !is VaultState.Envelope) return@launch
            // Design §6.6 pre-conditions: a wrap exists, the switch is on, the counter allows it.
            if (state.wrappedDekBiometric == null || state.isBiometricUnlockDisabled) return@launch
            if (!userDetailsRepository.shouldStartBiometricAuthFlow()) return@launch
            val probe = withContext(dispatchersProvider.default) {
                probeWrapKey(WrapKeySpec.BIOMETRIC, state.biometricKeyAliasSuffix)
            }
            when (probe) {
                is WrapKeyProbe.Ready -> _uiState.update { it.copy(biometricUnlockCipher = probe.cipher) }
                WrapKeyProbe.AwaitingAuthentication -> {
                    // KEK_Biometric is per-operation on every supported API; a time-bound one is a keygen defect.
                    Utils.crashInDebugBuild("biometric wrap key is time-bound")
                    onBiometricKeyInvalidated()
                }

                WrapKeyProbe.Invalidated -> onBiometricKeyInvalidated()
            }
        }
    }

    /**
     * Design §6.6: the wrap goes, the banner asks for the password, and the alias is rotated and
     * the wrap recreated by `VaultKeyMaintenance` after that login.
     */
    private suspend fun onBiometricKeyInvalidated() {
        Timber.w("biometric wrap key invalidated; deleting the biometric wrap")
        vaultRepository.deleteWrap(WrapKeySpec.BIOMETRIC)
        analyticsHelper.logEvent(AnalyticsKey.BIOMETRIC_KEY_INVALIDATED)
        _uiState.update { it.copy(isBiometricKeyInvalidated = true, biometricUnlockCipher = null) }
    }

    private fun onBiometricSuccess(cipher: Cipher?) {
        Timber.i("device security auth success")
        if (cipher == null) {
            // The prompt is always crypto-bound for login; a success without the cipher unlocks nothing.
            Utils.crashInDebugBuild("biometric prompt succeeded without the bound cipher")
            onBiometricError()
            return
        }
        launchUnlock {
            // The cipher is single-use; the prompt must not be re-launched with it.
            _uiState.update { it.copy(biometricUnlockCipher = null) }
            val state = vaultRepository.getVaultState() as? VaultState.Envelope
            val wrappedBiometric = state?.wrappedDekBiometric
            if (wrappedBiometric == null) {
                Utils.crashInDebugBuild("biometric prompt succeeded but the biometric wrap is gone")
                return@launchUnlock
            }
            val verifiedDek = withContext(dispatchersProvider.default) {
                val dek = try {
                    envelopeEncryptionManager.unwrapWithCipher(cipher, wrappedBiometric)
                } catch (e: Exception) {
                    flagIfOutsideCryptoApi("biometric wrap unwrap failed outside the crypto contract", e)
                    Timber.w(e, "biometric unwrap failed after a successful prompt")
                    return@withContext null
                }
                if (!envelopeEncryptionManager.verify(dek, state.dekVerifier)) {
                    dek.fill(0)
                    return@withContext null
                }
                dek
            }
            if (verifiedDek == null) {
                // A failure right after a successful prompt is an invalidation (design §2.2).
                onBiometricKeyInvalidated()
                return@launchUnlock
            }
            try {
                sessionKeyHolder.setSessionKey(verifiedDek)
            } finally {
                verifiedDek.fill(0)
            }
            Timber.i("envelope vault unlocked with biometric")
            vaultKeyMaintenance.scheduleAfterUnlock()
            onAuthSuccess(LoginMethod.BIOMETRIC)
        }
    }

    private fun onBiometricError() {
        Timber.i("biometric error or cancelled")
        _uiState.update { it.copy(biometricUnlockCipher = null) }
    }

    /**
     * `Cipher.init` on the private half is the health probe of design §2.2 and the prompt's
     * `CryptoObject` in one call. Every failure other than the time-bound window maps to
     * *invalidated* (design §6.10: the wrap is disposable, the DEK is never at stake).
     */
    private fun probeWrapKey(spec: WrapKeySpec, aliasSuffix: String?): WrapKeyProbe = try {
        WrapKeyProbe.Ready(envelopeEncryptionManager.decryptCipherFor(spec, aliasSuffix))
    } catch (_: UserNotAuthenticatedException) {
        WrapKeyProbe.AwaitingAuthentication
    } catch (_: KeyPermanentlyInvalidatedException) {
        WrapKeyProbe.Invalidated
    } catch (e: Exception) {
        flagIfOutsideCryptoApi("wrap key probe failed outside the crypto API", e)
        Timber.w(e, "%s wrap key unusable", spec.wireName)
        WrapKeyProbe.Invalidated
    }

    /** Crypto-API failures vary by OEM and are expected; any other class is a programming error. */
    private fun flagIfOutsideCryptoApi(message: String, error: Exception) {
        if (error !is GeneralSecurityException && error !is ProviderException && error !is IllegalArgumentException) {
            Utils.crashInDebugBuild(message, error)
        }
    }

    private sealed interface WrapKeyProbe {
        class Ready(val cipher: Cipher) : WrapKeyProbe
        data object AwaitingAuthentication : WrapKeyProbe
        data object Invalidated : WrapKeyProbe
    }

    private sealed interface RecoveryOutcome {
        class Recovered(val dek: ByteArray) : RecoveryOutcome
        data object NotAuthenticated : RecoveryOutcome
        data object WrongDek : RecoveryOutcome
        class KeystoreFailure(val cause: Exception) : RecoveryOutcome
    }

    private var isErasing = false

    private fun onEraseVaultConfirmClicked() {
        logFromEraseState(AnalyticsKey.ERASE_VAULT_DIALOG_CONFIRM_CLICK)
        if (isErasing) return
        isErasing = true
        viewModelScope.launch {
            try {
                vaultResetUseCase.get().eraseVault()
                _uiState.update { it.copy(isVaultErased = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // A half-done erase is a defect to see, not a state to hide; the screen stays put.
                Utils.crashInDebugBuild("vault erase failed", e)
            } finally {
                isErasing = false
            }
        }
    }

    private var isUnlocking = false

    /**
     * Serialises the unlock paths: a credential arriving while another is being verified is
     * dropped, as is anything after [UserAuthState.VERIFIED].
     */
    private fun launchUnlock(block: suspend () -> Unit) {
        if (isUnlocking || _uiState.value.userAuthState == UserAuthState.VERIFIED) return
        isUnlocking = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                isUnlocking = false
            }
        }
    }

    private fun onLoginClicked(password: String) = launchUnlock {
        Timber.i("login clicked")
        when (val state = vaultRepository.getVaultState()) {
            is VaultState.Envelope -> unlockEnvelopeWithPassword(password, state)
            is VaultState.Legacy -> unlockLegacyWithPassword(password)
            VaultState.CorruptedEnvelope -> onCorruptedEnvelopeState()
        }
    }

    private fun onCorruptedEnvelopeState() {
        _uiState.update {
            it.copy(
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
                failedLoginAttempts = it.failedLoginAttempts + 1,
            )
        }
        Utils.crashInDebugBuild("Corrupted envelope state on login")
    }

    private suspend fun unlockEnvelopeWithPassword(
        password: String,
        state: VaultState.Envelope,
    ) {
        Timber.i("unlocking envelope vault with password")
        val verifiedDek = withContext(dispatchersProvider.default) {
            val dek = envelopeEncryptionManager.unwrapWithPassword(
                password = password.toCharArray(),
                params = state.kdfParams,
                blob = state.wrappedDekPassword,
            ).getOrNull() ?: return@withContext null
            if (!envelopeEncryptionManager.verify(dek, state.dekVerifier)) {
                dek.fill(0)
                return@withContext null
            }
            dek
        }
        if (verifiedDek == null) {
            Timber.i("envelope password verification failed")
            onIncorrectPassword()
            return
        }
        try {
            // The session must be open before the enqueue below: the worker reads every record
            // through the holder and may start at once.
            sessionKeyHolder.setSessionKey(verifiedDek)
        } finally {
            verifiedDek.fill(0)
        }
        Timber.i("envelope vault unlocked with password")
        enqueueAutoBackupIfEnabled(password)
        vaultKeyMaintenance.scheduleAfterUnlock()
        onAuthSuccess(LoginMethod.PASSWORD)
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    private suspend fun unlockLegacyWithPassword(password: String) {
        _uiState.update { it.copy(isLegacyVault = true) }
        val isPasswordCorrect = userDetailsRepository.checkPassword(password)
        Timber.i("is password correct: $isPasswordCorrect")
        if (!isPasswordCorrect) {
            onIncorrectPassword()
            return
        }
        runLegacyMigration(
            password = password,
            newHint = null,
            trigger = MigrationTrigger.LOGIN,
        )
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    private suspend fun runLegacyMigration(
        password: String,
        newHint: String?,
        trigger: MigrationTrigger,
    ) {
        Timber.i("starting legacy vault migration: trigger=%s", trigger.wireName)
        pendingMigrationPassword = password
        pendingMigrationHint = newHint
        pendingMigrationTrigger = trigger
        _uiState.update { it.copy(migrationFailure = null) }

        when (
            val outcome = legacyVaultMigrator.get().migrate(
                password = password.toCharArray(),
                newHint = newHint,
                trigger = trigger,
            )
        ) {
            is MigrationOutcome.Success -> {
                Timber.i("legacy vault migration succeeded")
                outcome.dek.fill(0)
                clearPendingMigrationCredentials()
                _uiState.update { it.copy(isLegacyVault = false, migrationFailure = null) }
                if (trigger == MigrationTrigger.LOGIN) {
                    enqueueAutoBackupIfEnabled(password)
                }
                vaultKeyMaintenance.scheduleAfterUnlock()
                val method = if (trigger == MigrationTrigger.FORGOT_PASSWORD) {
                    LoginMethod.PASSWORD_RESET
                } else {
                    LoginMethod.PASSWORD
                }
                onAuthSuccess(method)
            }

            MigrationOutcome.AlreadyMigrated -> {
                Timber.i("vault already migrated; unlocking as envelope")
                clearPendingMigrationCredentials()
                _uiState.update { it.copy(isLegacyVault = false, migrationFailure = null) }
                val state = vaultRepository.getVaultState()
                if (state is VaultState.Envelope) {
                    unlockEnvelopeWithPassword(password, state)
                }
            }

            is MigrationOutcome.Failure -> {
                Timber.w(
                    "legacy vault migration failed: stage=%s, reason=%s, attempt=%d",
                    outcome.stage.wireName,
                    outcome.reason.wireName,
                    outcome.attempt,
                )
                if (outcome.reason == MigrationFailureReason.LEGACY_KEY_LOST) {
                    clearPendingMigrationCredentials()
                    _uiState.update {
                        it.copy(
                            userAuthState = UserAuthState.LEGACY_KEY_LOST,
                            migrationFailure = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            migrationFailure = MigrationFailureUiState(
                                reason = outcome.reason,
                                trigger = outcome.trigger,
                                attempt = outcome.attempt,
                            ),
                        )
                    }
                }
            }
        }
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    private fun onMigrationFailureShown(reason: MigrationFailureReason) {
        analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_FAILURE_SHOW) {
            param(AnalyticsParam.REASON, reason.wireName)
        }
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    private fun onMigrationRetryClicked() {
        val failure = _uiState.value.migrationFailure ?: return
        val password = pendingMigrationPassword ?: return
        val hint = pendingMigrationHint
        val trigger = pendingMigrationTrigger
        Timber.i("migration retry clicked: reason=%s, attempt=%d", failure.reason.wireName, failure.attempt)
        analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_RETRY_CLICK) {
            param(AnalyticsParam.REASON, failure.reason.wireName)
        }
        viewModelScope.launch {
            runLegacyMigration(
                password = password,
                newHint = hint,
                trigger = trigger,
            )
        }
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    private fun onMigrationContinueLegacyClicked() {
        val failure = _uiState.value.migrationFailure ?: return
        if (!failure.canContinueLegacy) return
        Timber.i("migration continue legacy clicked: reason=%s, attempt=%d", failure.reason.wireName, failure.attempt)
        analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_CONTINUE_LEGACY_CLICK) {
            param(AnalyticsParam.REASON, failure.reason.wireName)
        }
        viewModelScope.launch {
            if (!openLegacySession()) return@launch
            analyticsHelper.logEvent(AnalyticsKey.VAULT_LEGACY_SESSION_START) {
                param(AnalyticsParam.ATTEMPT, failure.attempt)
            }
            val password = pendingMigrationPassword
            clearPendingMigrationCredentials()
            _uiState.update { it.copy(migrationFailure = null) }
            if (password != null) {
                enqueueAutoBackupIfEnabled(password)
            }
            onAuthSuccess(LoginMethod.LEGACY_SESSION)
        }
    }

    private fun clearPendingMigrationCredentials() {
        pendingMigrationPassword = null
        pendingMigrationHint = null
    }

    private suspend fun enqueueAutoBackupIfEnabled(password: String) {
        if (settingsDataStore.getAutoBackupAfterPasswordLogin()) {
            Timber.i("enqueuing auto backup request after login with pswrd")
            BackupDataWorker.enqueueRequest(
                password = password,
                showBackupStartNotification = false,
                workManager = workManager.get(),
                backupPasswordRegistry = backupPasswordRegistry,
            )
        } else {
            Timber.i("auto backup disabled from settings")
        }
    }

    /**
     * Opens a legacy vault session (`LegacyUnlocked`) using the Keystore handle of `symmetricDataKey`.
     *
     * Used by the migration fallback (*Continue for now*). A missing handle transitions to
     * [UserAuthState.LEGACY_KEY_LOST].
     *
     * @return `false` when the legacy key is gone and the unlock must not continue.
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    private fun openLegacySession(): Boolean {
        val legacyKey = keystoreGateway.legacyVaultKey()
        if (legacyKey == null) {
            Timber.w("legacy vault key is missing; the vault cannot be opened")
            _uiState.update { it.copy(userAuthState = UserAuthState.LEGACY_KEY_LOST) }
            return false
        }
        sessionKeyHolder.setLegacySession(legacyKey)
        return true
    }

    /** Single exit of every unlock path. */
    private suspend fun onAuthSuccess(method: LoginMethod) {
        val loginCount = userDetailsRepository.onAuthSuccess(withBiometric = method == LoginMethod.BIOMETRIC)
        // Telemetry must not be able to stop an unlock: a failed read only drops these params.
        val deviceSecurity = runCatching { deviceSecurityStatus.snapshot() }.getOrNull()
        analyticsHelper.logEvent(AnalyticsKey.LOGIN_SUCCESS) {
            param(AnalyticsParam.LOGIN_METHOD, method.analyticsValue)
            param(AnalyticsParam.LOGIN_COUNT, loginCount)
            if (deviceSecurity != null) {
                param(AnalyticsParam.DEVICE_SECURE, deviceSecurity.isDeviceSecure)
                param(AnalyticsParam.BIOMETRIC_CLASS, deviceSecurity.biometricClass.analyticsValue)
                param(AnalyticsParam.STRONGBOX, deviceSecurity.hasStrongBox)
            }
        }
        _uiState.update {
            it.copy(
                userAuthState = UserAuthState.VERIFIED,
            )
        }
    }

    private fun onIncorrectPassword() {
        analyticsHelper.logEvent(AnalyticsKey.LOGIN_FAILED)
        _uiState.update {
            it.copy(
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
                failedLoginAttempts = it.failedLoginAttempts + 1,
            )
        }
    }

    private fun getHintFromDb() {
        viewModelScope.launch {
            Timber.i("getting hint")
            _uiState.update {
                it.copy(
                    hint = userDetailsRepository.getHint() ?: "",
                )
            }
        }
    }

    private companion object {
        /** `exception` value when a recovery failed without an exception (verifier mismatch, missed window). */
        const val NO_EXCEPTION = "none"
    }
}