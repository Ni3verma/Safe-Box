package com.andryoga.composeapp.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.BuildConfig
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.AnimatedCurveBackground
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.theme.SafeBoxTheme


@Composable
fun LoginScreenRoot(onLoginSuccess: () -> Unit) {
    val viewModel = hiltViewModel<LoginViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.passwordValidatorState == PasswordValidatorState.VERIFIED) {
        onLoginSuccess()
    }

    LoginScreen(
        uiState = uiState,
        screenAction = viewModel::onAction
    )
}
@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AnimatedCurveBackground()

        Text(
            text = stringResource(R.string.welcome_back),
            color = colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        ) {
            LoginCardContent(
                uiState = uiState,
                screenAction = screenAction
            )
        }
    }
}

@Composable
private fun LoginCardContent(
    uiState: LoginUiState,
    screenAction: (LoginScreenAction) -> Unit
) {
    var password by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) "Qwerty@@135" else ""
        )
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val isPasswordFieldError = uiState.passwordValidatorState == PasswordValidatorState.INCORRECT

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = colorScheme.primary.copy(0.8f),
                    shape = RoundedCornerShape(percent = 50)
                )
                .padding(16.dp),
            tint = Color.White
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
                        contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility)
                    )
                }
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
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

        if (showHint) {
            Text(
                text = uiState.hint,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = { screenAction(LoginScreenAction.LoginClicked(password = password)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.login))
        }
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
                passwordValidatorState = PasswordValidatorState.INCORRECT
            ),
            screenAction = {}
        )
    }
}