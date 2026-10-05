package com.andryoga.safebox.ui.login

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.AuthScreenLayout
import com.andryoga.safebox.ui.core.DeviceSecurityAuthHandler
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.canAuthenticateUsingDeviceSecurity
import com.andryoga.safebox.ui.core.motion.rememberRejectShake
import com.andryoga.safebox.ui.core.password.DeviceSecurityRequiredDialog
import com.andryoga.safebox.ui.core.password.UpdatePasswordDialog
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import timber.log.Timber

@Composable
fun LoginScreenRoot(onLoginSuccess: () -> Unit) {
    val viewModel = hiltViewModel<LoginViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.userAuthState) {
        if (uiState.userAuthState == UserAuthState.VERIFIED) {
            onLoginSuccess()
        }
    }

    LoginScreen(
        uiState = uiState,
        screenAction = viewModel::onAction
    )
}

@VisibleForTesting
@Composable
internal fun LoginScreen(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit
) {
    val context = LocalContext.current
    val biometricAuthProvider = LocalDeviceSecurityAuthProvider.current
    var showResetPasswordAuth by remember { mutableStateOf(false) }
    var showUpdatePasswordDialog by remember { mutableStateOf(false) }
    var showDeviceSecurityRequiredDialog by remember { mutableStateOf(false) }

    AuthScreenLayout(title = stringResource(R.string.welcome_back)) {
        LoginCardContent(
            uiState = uiState,
            screenAction = screenAction,
            onForgotPasswordClick = {
                Timber.i("forgot password clicked on login screen, checking device security auth availability")
                if (
                    canAuthenticateUsingDeviceSecurity(
                        context,
                        biometricAuthProvider,
                        allowDeviceCredential = true,
                    )
                ) {
                    showResetPasswordAuth = true
                } else {
                    showDeviceSecurityRequiredDialog = true
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (canAuthenticateUsingDeviceSecurity(context, biometricAuthProvider)) {
            screenAction(LoginScreenAction.BiometricAvailable)
        }
    }

    if (uiState.canUnlockWithBiometric) {
        DeviceSecurityAuthHandler(
            onSuccess = { screenAction(LoginScreenAction.BiometricSuccess) },
            onErrorOrCancel = { screenAction(LoginScreenAction.BiometricError) }
        )
    }

    if (showResetPasswordAuth) {
        DeviceSecurityAuthHandler(
            title = stringResource(R.string.forgot_password),
            subtitle = stringResource(R.string.forgot_password_device_security_subtitle),
            allowDeviceCredential = true,
            onSuccess = {
                showResetPasswordAuth = false
                showUpdatePasswordDialog = true
            },
            onErrorOrCancel = {
                Timber.w("device security auth error or cancel for forgot password on login screen")
                showResetPasswordAuth = false
            }
        )
    }

    if (showUpdatePasswordDialog) {
        UpdatePasswordDialog(
            onDismiss = {
                showUpdatePasswordDialog = false
                screenAction(LoginScreenAction.OnUpdatePasswordDismissClicked)
            },
            onShow = {
                screenAction(LoginScreenAction.OnUpdatePasswordDialogShown)
            },
            onSave = { newPassword, hint ->
                showUpdatePasswordDialog = false
                screenAction(LoginScreenAction.OnResetPassword(newPassword, hint))
            }
        )
    }

    if (showDeviceSecurityRequiredDialog) {
        DeviceSecurityRequiredDialog(
            onDismiss = {
                showDeviceSecurityRequiredDialog = false
                screenAction(LoginScreenAction.OnDeviceSecurityRequiredDismissClicked)
            },
            onShow = {
                screenAction(LoginScreenAction.OnDeviceSecurityRequiredDialogShown)
            },
            onOpenSettingsClick = {
                showDeviceSecurityRequiredDialog = false
                screenAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked)
            },
            onOpenSettingsFailure = {
                screenAction(LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed)
            },
        )
    }
}

/**
 * The login form inside the auth card. The password field shakes (with a reject haptic) every
 * time an attempt is rejected, keyed on [LoginUiState.failedLoginAttempts] so a second wrong
 * password shakes just like the first; the hint expands into place instead of appearing.
 */
@Composable
private fun ColumnScope.LoginCardContent(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit,
    onForgotPasswordClick: () -> Unit = {}
) {
    var password by remember(uiState.defaultPassword) {
        mutableStateOf(uiState.defaultPassword)
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val isPasswordFieldError = uiState.userAuthState == UserAuthState.INCORRECT_PASSWORD_ENTERED
    val rejectShake = rememberRejectShake(trigger = uiState.failedLoginAttempts)

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
                    contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility)
                )
            }
        },
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .then(rejectShake)
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
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Text(
            text = uiState.hint,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
            textAlign = TextAlign.Center
        )
    }

    Button(
        onClick = { screenAction(LoginScreenAction.LoginClicked(password = password)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        enabled = password.isBlank().not()
    ) {
        Text(stringResource(R.string.login))
    }

    TextButton(
        onClick = onForgotPasswordClick,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(stringResource(R.string.forgot_password))
    }
}

@LightDarkModePreview
@Composable
private fun LoginPreview() {
    SafeBoxTheme {
        LoginScreen(
            uiState = LoginUiState(),
            screenAction = {}
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
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED
            ),
            screenAction = {}
        )
    }
}