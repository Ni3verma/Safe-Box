package com.andryoga.safebox.ui.signup

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.AuthScreenLayout
import com.andryoga.safebox.ui.core.MandatoryLabelText
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.signup.components.PasswordTextField
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun SignupScreenRoot(onSignupSuccess: () -> Unit) {
    val viewModel = hiltViewModel<SignupViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val navigateToHome by viewModel.navigateToHome.collectAsState()

    LaunchedEffect(navigateToHome) {
        if (navigateToHome) {
            onSignupSuccess()
        }
    }

    SignupScreen(
        uiState = uiState,
        screenAction = viewModel::onAction,
    )
}

@VisibleForTesting
@Composable
internal fun SignupScreen(
    uiState: SignupUiState,
    screenAction: (SignupScreenAction) -> Unit,
) {
    AuthScreenLayout(title = stringResource(R.string.welcome)) {
        SignupCardContent(
            uiState = uiState,
            screenAction = screenAction
        )
    }
}

@Composable
private fun SignupCardContent(
    uiState: SignupUiState,
    screenAction: (SignupScreenAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    PasswordTextField(
        uiState = uiState,
        screenAction = screenAction,
        focusManager = focusManager,
    )

    OutlinedTextField(
        value = uiState.hint,
        onValueChange = { screenAction(SignupScreenAction.OnHintUpdate(it)) },
        label = { MandatoryLabelText(stringResource(R.string.hint)) },
        placeholder = { Text(stringResource(R.string.enter_hint)) },
        singleLine = true,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
    )

    Button(
        onClick = { screenAction(SignupScreenAction.OnSignupClick) },
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        enabled = uiState.isSignupButtonEnabled,
    ) {
        Text(stringResource(R.string.signup))
    }
}

@LightDarkModePreview
@Composable
private fun GreetingPreview() {
    SafeBoxTheme {
        SignupScreen(
            uiState = SignupUiState(isSignupButtonEnabled = true),
            screenAction = { }
        )
    }
}

@LightDarkModePreview
@Composable
private fun GreetingPreviewWithPasswordError() {
    SafeBoxTheme {
        SignupScreen(
            uiState = SignupUiState(
                password = "Hey",
                hint = "this is hint",
                isPasswordFieldError = true,
                passwordValidatorState = PasswordValidatorState.SHORT_PASSWORD_LENGTH
            ),
            screenAction = { }
        )
    }
}