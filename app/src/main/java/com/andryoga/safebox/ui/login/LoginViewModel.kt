package com.andryoga.safebox.ui.login

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
import com.andryoga.safebox.security.envelope.migration.LegacyVaultMigrator
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationOutcome
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.security.envelope.migration.VaultMigrationProgress
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.SessionLockReasonStore
import com.andryoga.safebox.worker.BackupDataWorker
import com.andryoga.safebox.worker.BackupPasswordRegistry
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userDetailsRepository: UserDetailsRepository,
    private val vaultRepository: VaultRepository,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val legacyVaultMigrator: Lazy<LegacyVaultMigrator>,
    private val vaultMigrationProgress: VaultMigrationProgress,
    private val vaultKeyMaintenance: VaultKeyMaintenance,
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
            LoginScreenAction.BiometricSuccess -> onBiometricSuccess()
            LoginScreenAction.BiometricAvailable -> onBiometricAvailable()
            LoginScreenAction.BiometricError -> onBiometricError()
            is LoginScreenAction.OnResetPassword -> onResetPassword(action.newPassword, action.hint)
            LoginScreenAction.ForgotPasswordClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.FORGOT_PASSWORD_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDialogShown -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked -> {
                activeSessionManager.setPaused(true)
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDismissClicked -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }

            LoginScreenAction.OnUpdatePasswordDialogShown -> {
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            LoginScreenAction.OnUpdatePasswordDismissClicked -> {
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
            }

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

    private fun onResetPassword(newPassword: String, hint: String) {
        Timber.i("resetting master password and hint from login screen")
        logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        viewModelScope.launch {
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

                is VaultState.Envelope -> {
                    // TODO(MR6): replace with KEK_Device unwrap and VaultRepository.rewrapPassword
                    if (!openLegacySession()) return@launch
                    userDetailsRepository.updatePasswordAndHint(newPassword, hint)
                    Timber.i("master password and hint successfully updated from login screen")
                    onAuthSuccess(LoginMethod.PASSWORD_RESET)
                }

                VaultState.CorruptedEnvelope -> onCorruptedEnvelopeState()
            }
        }
    }

    private fun onBiometricAvailable() {
        Timber.i("biometric capability is available")
        viewModelScope.launch {
            val state = vaultRepository.getVaultState()
            if (state is VaultState.Legacy) {
                // Biometric login is suppressed while crypto_version = 1.
                _uiState.update { it.copy(isLegacyVault = true, canUnlockWithBiometric = false) }
                return@launch
            }
            if (userDetailsRepository.shouldStartBiometricAuthFlow()) {
                _uiState.update {
                    it.copy(canUnlockWithBiometric = true)
                }
            }
        }
    }

    // TODO(MR6): replace with CryptoObject unwrap of wrapped_dek_biometric
    private fun onBiometricSuccess() {
        Timber.i("device security auth success")
        viewModelScope.launch {
            if (!openLegacySession()) return@launch
            onAuthSuccess(LoginMethod.BIOMETRIC)
        }
    }

    private fun onBiometricError() {
        Timber.i("biometric error or cancelled")
        _uiState.update {
            it.copy(canUnlockWithBiometric = false)
        }
    }

    private fun onLoginClicked(password: String) {
        Timber.i("login clicked")
        viewModelScope.launch {
            when (val state = vaultRepository.getVaultState()) {
                is VaultState.Envelope -> unlockEnvelopeWithPassword(password, state)
                is VaultState.Legacy -> unlockLegacyWithPassword(password)
                VaultState.CorruptedEnvelope -> onCorruptedEnvelopeState()
            }
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
                clearPendingMigrationCredentials()
                _uiState.update { it.copy(isLegacyVault = false, migrationFailure = null) }
                val state = vaultRepository.getVaultState()
                if (state is VaultState.Envelope) {
                    unlockEnvelopeWithPassword(password, state)
                }
            }

            is MigrationOutcome.Failure -> {
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
     * Used by the migration fallback (*Continue for now*) and, until MR 6, by the unmigrated
     * biometric/reset branches. A missing handle transitions to [UserAuthState.LEGACY_KEY_LOST].
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
}