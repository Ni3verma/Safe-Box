package com.andryoga.safebox.ui.login

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationProgressState
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.ui.core.AnimatedCurveBackground
import com.andryoga.safebox.ui.core.DeviceSecurityAuthHandler
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.SessionLockReason
import com.andryoga.safebox.ui.core.canAuthenticateUsingDeviceSecurity
import com.andryoga.safebox.ui.core.motion.rejectShake
import com.andryoga.safebox.ui.core.password.DeviceSecurityRequiredDialog
import com.andryoga.safebox.ui.core.password.UpdatePasswordDialog
import com.andryoga.safebox.ui.core.vault.EraseVaultDialog
import com.andryoga.safebox.ui.login.components.LegacyKeyLostCardContent
import com.andryoga.safebox.ui.login.components.LoginNoticeBanner
import com.andryoga.safebox.ui.login.components.MigrationFailureCardContent
import com.andryoga.safebox.ui.login.components.MigrationProgressCardContent
import com.andryoga.safebox.ui.login.components.RecoveryNotSetUpCardContent
import com.andryoga.safebox.ui.login.components.recoveryFailureText
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import timber.log.Timber

@Composable
fun LoginScreenRoot(
    onLoginSuccess: () -> Unit,
    onVaultErased: () -> Unit,
) {
    val viewModel = hiltViewModel<LoginViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.userAuthState) {
        if (uiState.userAuthState == UserAuthState.VERIFIED) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.isVaultErased) {
        if (uiState.isVaultErased) {
            onVaultErased()
        }
    }

    LoginScreen(
        uiState = uiState,
        screenAction = viewModel::onAction,
    )
}

@VisibleForTesting
@Composable
internal fun LoginScreen(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit,
) {
    val context = LocalContext.current
    val biometricAuthProvider = LocalDeviceSecurityAuthProvider.current
    var showEraseVaultDialog by remember { mutableStateOf(false) }
    val migrationRunning = uiState.migrationProgress as? MigrationProgressState.Running

    if (migrationRunning != null) {
        BackHandler(enabled = true) {
            // Non-dismissable while the vault migration is in flight.
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        AnimatedCurveBackground()

        Text(
            text = stringResource(R.string.welcome_back),
            color = colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        ) {
            when {
                migrationRunning != null -> {
                    MigrationProgressCardContent(progress = migrationRunning)
                }

                uiState.migrationFailure != null -> {
                    MigrationFailureCardContent(
                        failure = uiState.migrationFailure,
                        screenAction = screenAction,
                    )
                }

                uiState.userAuthState == UserAuthState.LEGACY_KEY_LOST -> {
                    LegacyKeyLostCardContent(
                        onEraseVaultClick = { showEraseVaultDialog = true },
                    )
                }

                uiState.isRecoveryNotSetUp -> {
                    RecoveryNotSetUpCardContent(
                        onEraseVaultClick = { showEraseVaultDialog = true },
                        onBackClick = { screenAction(LoginScreenAction.OnRecoveryNotSetUpDismissed) },
                    )
                }

                else -> {
                    LoginCardContent(
                        uiState = uiState,
                        screenAction = screenAction,
                        onForgotPasswordClick = {
                            Timber.i("forgot password clicked on login screen")
                            screenAction(LoginScreenAction.ForgotPasswordClicked)
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (canAuthenticateUsingDeviceSecurity(context, biometricAuthProvider)) {
            screenAction(LoginScreenAction.BiometricAvailable)
        }
    }

    val biometricCipher = uiState.biometricUnlockCipher
    val isLoginFormShowing = migrationRunning == null && uiState.migrationFailure == null
    if (biometricCipher != null && isLoginFormShowing && uiState.forgotPasswordStep == null) {
        // Remembered per cipher: a new CryptoObject instance would re-launch the prompt.
        val cryptoObject = remember(biometricCipher) { BiometricPrompt.CryptoObject(biometricCipher) }
        DeviceSecurityAuthHandler(
            cryptoObject = cryptoObject,
            onSuccess = { screenAction(LoginScreenAction.BiometricSuccess(it?.cipher)) },
            onErrorOrCancel = { screenAction(LoginScreenAction.BiometricError) },
        )
    }

    ForgotPasswordFlow(
        step = uiState.forgotPasswordStep,
        screenAction = screenAction,
    )

    if (showEraseVaultDialog) {
        EraseVaultDialog(
            onDismiss = {
                showEraseVaultDialog = false
                screenAction(LoginScreenAction.OnEraseVaultCancelClicked)
            },
            onShow = {
                screenAction(LoginScreenAction.OnEraseVaultDialogShown)
            },
            onConfirm = {
                showEraseVaultDialog = false
                screenAction(LoginScreenAction.OnEraseVaultConfirmClicked)
            },
        )
    }
}

/** The prompt or dialog of the current [ForgotPasswordStep]; nothing when the flow is idle. */
@Composable
private fun ForgotPasswordFlow(
    step: ForgotPasswordStep?,
    screenAction: (LoginScreenAction) -> Unit,
) {
    val context = LocalContext.current
    val biometricAuthProvider = LocalDeviceSecurityAuthProvider.current
    when (step) {
        null -> Unit

        ForgotPasswordStep.LegacyAuthentication -> {
            // Decided once per step: the legacy gate is the only place the dialog is still honest (design §6.4.1).
            val canAuthenticate = remember(step) {
                canAuthenticateUsingDeviceSecurity(context, biometricAuthProvider, allowDeviceCredential = true)
            }
            if (canAuthenticate) {
                DeviceSecurityAuthHandler(
                    title = stringResource(R.string.forgot_password),
                    subtitle = stringResource(R.string.forgot_password_device_security_subtitle),
                    allowDeviceCredential = true,
                    onSuccess = { screenAction(LoginScreenAction.OnLegacyResetAuthenticated) },
                    onErrorOrCancel = {
                        Timber.w("device security auth error or cancel for forgot password on login screen")
                        screenAction(LoginScreenAction.OnForgotPasswordAuthCancelled)
                    },
                )
            } else {
                DeviceSecurityRequiredDialog(
                    onDismiss = { screenAction(LoginScreenAction.OnDeviceSecurityRequiredDismissClicked) },
                    onShow = { screenAction(LoginScreenAction.OnDeviceSecurityRequiredDialogShown) },
                    onOpenSettingsClick = {
                        screenAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked)
                    },
                    onOpenSettingsFailure = {
                        screenAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed)
                    },
                )
            }
        }

        is ForgotPasswordStep.DeviceAuthentication -> key(step) {
            // Keyed on the step instance: the API 29 re-prompt carries the same null cipher and
            // must still open a fresh prompt.
            val cryptoObject = remember { step.cipher?.let { BiometricPrompt.CryptoObject(it) } }
            DeviceSecurityAuthHandler(
                title = stringResource(R.string.forgot_password),
                subtitle = stringResource(R.string.forgot_password_device_security_subtitle),
                allowDeviceCredential = true,
                cryptoObject = cryptoObject,
                onSuccess = { screenAction(LoginScreenAction.OnRecoveryAuthenticated(it?.cipher)) },
                onErrorOrCancel = {
                    Timber.w("device security auth error or cancel for vault recovery on login screen")
                    screenAction(LoginScreenAction.OnForgotPasswordAuthCancelled)
                },
            )
        }

        ForgotPasswordStep.NewPassword -> {
            UpdatePasswordDialog(
                onDismiss = { screenAction(LoginScreenAction.OnUpdatePasswordDismissClicked) },
                onShow = { screenAction(LoginScreenAction.OnUpdatePasswordDialogShown) },
                onSave = { newPassword, hint ->
                    screenAction(LoginScreenAction.OnResetPassword(newPassword, hint))
                },
            )
        }
    }
}

@Composable
private fun LoginCardContent(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit,
    onForgotPasswordClick: () -> Unit = {},
) {
    var password by remember(uiState.defaultPassword) {
        mutableStateOf(uiState.defaultPassword)
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val isPasswordFieldError = uiState.userAuthState == UserAuthState.INCORRECT_PASSWORD_ENTERED

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val lockNotice = when (uiState.lockReason) {
            SessionLockReason.AWAY_TIMEOUT -> stringResource(R.string.login_away_banner)
            null -> null
        }
        if (lockNotice != null) {
            LoginNoticeBanner(
                text = lockNotice,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (uiState.isLegacyVault) {
            LoginNoticeBanner(
                text = stringResource(R.string.login_security_upgrade_notice),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        if (uiState.isBiometricKeyInvalidated) {
            LoginNoticeBanner(
                text = stringResource(R.string.login_biometric_invalidated_banner),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (uiState.recoveryFailure != null) {
            LoginNoticeBanner(
                text = recoveryFailureText(uiState.recoveryFailure),
                isError = true,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = colorScheme.primary.copy(0.8f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .padding(16.dp),
            tint = Color.White,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            placeholder = { Text(stringResource(R.string.password)) },
            singleLine = true,
            isError = isPasswordFieldError,
            supportingText = {
                if (isPasswordFieldError) {
                    Text(text = stringResource(R.string.incorrect_pswrd_message))
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image =
                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        image,
                        contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility),
                    )
                }
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .rejectShake(uiState.failedLoginAttempts),
        )

        TextButton(onClick = {
            showHint = !showHint
            screenAction(LoginScreenAction.ShowHintClicked)
        }) {
            if (showHint) {
                Text(stringResource(R.string.hide_hint))
            } else {
                Text(stringResource(R.string.show_hint))
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = uiState.hint,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = { screenAction(LoginScreenAction.LoginClicked(password = password)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            enabled = password.isBlank().not(),
        ) {
            Text(stringResource(R.string.login))
        }

        TextButton(
            onClick = onForgotPasswordClick,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.forgot_password))
        }
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreview() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewAfterAwayTimeout() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(lockReason = SessionLockReason.AWAY_TIMEOUT),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewWrongPassword() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(
                hint = "This is hint",
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
            ),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewLegacyVault() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(isLegacyVault = true),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewMigrationProgress() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(
                isLegacyVault = true,
                migrationProgress = MigrationProgressState.Running(done = 18, total = 42),
            ),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewMigrationFailureLogin() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(
                isLegacyVault = true,
                migrationFailure = MigrationFailureUiState(
                    reason = MigrationFailureReason.DB_IO,
                    trigger = MigrationTrigger.LOGIN,
                ),
            ),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewMigrationFailureForgotPassword() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(
                isLegacyVault = true,
                migrationFailure = MigrationFailureUiState(
                    reason = MigrationFailureReason.DB_IO,
                    trigger = MigrationTrigger.FORGOT_PASSWORD,
                ),
            ),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewLegacyKeyLost() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(userAuthState = UserAuthState.LEGACY_KEY_LOST),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewBiometricKeyInvalidated() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(isBiometricKeyInvalidated = true),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewRecoveryNotSetUp() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(isRecoveryNotSetUp = true),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewRecoveryFailureVerifier() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(recoveryFailure = RecoveryFailureReason.VERIFIER),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewRecoveryFailureNotAuthenticated() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(recoveryFailure = RecoveryFailureReason.NOT_AUTHENTICATED),
            screenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreviewRecoveryNewPassword() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(forgotPasswordStep = ForgotPasswordStep.NewPassword),
            screenAction = {},
        )
    }
}