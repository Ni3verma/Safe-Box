package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.DeviceSecurityAuthHandler
import com.andryoga.safebox.ui.core.password.UpdatePasswordDialog
import com.andryoga.safebox.ui.home.settings.ChangePasswordStep
import com.andryoga.safebox.ui.home.settings.SettingsScreenAction

/**
 * Renders the one prompt or dialog of the current [ChangePasswordStep].
 *
 * The device prompt allows the screen-lock credential, so `BiometricPrompt` cannot carry a
 * negative button; cancelling it is the way to *use the master password instead*, and any prompt
 * error lands on the same field.
 */
@Composable
internal fun ChangePasswordFlow(
    step: ChangePasswordStep,
    onScreenAction: (SettingsScreenAction) -> Unit,
) {
    when (step) {
        ChangePasswordStep.DeviceAuthentication -> DeviceSecurityAuthHandler(
            title = stringResource(R.string.change_master_password),
            subtitle = stringResource(R.string.change_master_password_body),
            allowDeviceCredential = true,
            onSuccess = { onScreenAction(SettingsScreenAction.OnDeviceAuthSucceeded) },
            onErrorOrCancel = { onScreenAction(SettingsScreenAction.OnDeviceAuthFailedOrCancelled) },
        )

        is ChangePasswordStep.CurrentPassword -> CurrentPasswordDialog(
            isIncorrect = step.isIncorrect,
            isVerifying = step.isVerifying,
            onSubmit = { onScreenAction(SettingsScreenAction.OnCurrentPasswordSubmitted(it)) },
            onDismiss = { onScreenAction(SettingsScreenAction.OnCurrentPasswordDismissed) },
        )

        ChangePasswordStep.NewPassword -> UpdatePasswordDialog(
            onDismiss = { onScreenAction(SettingsScreenAction.OnUpdatePasswordDismissClicked) },
            onShow = { onScreenAction(SettingsScreenAction.OnUpdatePasswordDialogShown) },
            onSave = { newPassword, hint ->
                onScreenAction(SettingsScreenAction.OnUpdateMasterPassword(newPassword, hint))
            },
        )
    }
}
