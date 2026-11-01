package com.andryoga.safebox.ui.login.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.SessionLockReason
import com.andryoga.safebox.ui.core.motion.rejectShake
import com.andryoga.safebox.ui.login.LoginScreenAction
import com.andryoga.safebox.ui.login.LoginUiState
import com.andryoga.safebox.ui.login.UserAuthState

/**
 * The login form: notice banners, the password field with hint toggle, the login button and the
 * forgot-password link. Shown inside the login card whenever no migration, legacy-key-lost or
 * recovery-not-set-up state has replaced it.
 *
 * @param uiState The screen state the form reads banners, hint and error state from.
 * @param screenAction Sink for the [LoginScreenAction]s the form emits.
 * @param onForgotPasswordClick Invoked when the forgot-password link is tapped.
 */
@Composable
internal fun LoginCardContent(
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
