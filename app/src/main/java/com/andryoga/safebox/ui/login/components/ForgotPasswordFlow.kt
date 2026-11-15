package com.andryoga.safebox.ui.login.components

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.DeviceSecurityAuthHandler
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.canAuthenticateUsingDeviceSecurity
import com.andryoga.safebox.ui.core.password.DeviceSecurityRequiredDialog
import com.andryoga.safebox.ui.core.password.UpdatePasswordDialog
import com.andryoga.safebox.ui.login.ForgotPasswordStep
import com.andryoga.safebox.ui.login.LoginScreenAction
import timber.log.Timber

/**
 * The prompt or dialog of the current [ForgotPasswordStep]; nothing when the flow is idle. Each
 * step is a device prompt or a dialog layered over the login card, so this composable owns no
 * layout of its own.
 *
 * @param step The current step, or `null` while forgot password is not in progress.
 * @param screenAction Sink for the [LoginScreenAction]s each step emits.
 */
@Composable
internal fun ForgotPasswordFlow(
    step: ForgotPasswordStep?,
    screenAction: (LoginScreenAction) -> Unit,
) {
    val context = LocalContext.current
    val biometricAuthProvider = LocalDeviceSecurityAuthProvider.current
    when (step) {
        null -> Unit

        ForgotPasswordStep.LegacyAuthentication -> {
            // Decided once per step: the legacy gate is the only place the dialog is still honest.
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
