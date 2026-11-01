package com.andryoga.safebox.ui.login

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.andryoga.safebox.ui.login.components.EraseVaultDialog
import com.andryoga.safebox.ui.login.components.ForgotPasswordFlow
import com.andryoga.safebox.ui.login.components.LegacyKeyLostCardContent
import com.andryoga.safebox.ui.login.components.LoginCardContent
import com.andryoga.safebox.ui.login.components.MigrationFailureCardContent
import com.andryoga.safebox.ui.login.components.MigrationProgressCardContent
import com.andryoga.safebox.ui.login.components.RecoveryNotSetUpCardContent
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