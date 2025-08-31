package com.andryoga.composeapp.ui.signup

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.AnimatedCurveBackground
import com.andryoga.composeapp.ui.core.MandatoryText
import com.andryoga.composeapp.ui.signup.components.PasswordTextField
import com.andryoga.composeapp.ui.theme.SafeBoxTheme

@Composable
fun SignupScreenRoot(modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<SignupViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    SignupScreen(
        uiState = uiState,
        screenAction = viewModel::onAction,
        modifier = modifier
    )
}

@Composable
private fun SignupScreen(
    uiState: SignupUiState,
    screenAction: (SignupScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AnimatedCurveBackground()

        Text(
            text = stringResource(R.string.welcome),
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
                .imePadding()
        ) {
            SignupCardContent(
                uiState = uiState,
                screenAction = screenAction
            )
        }
    }
}

@Composable
private fun SignupCardContent(
    uiState: SignupUiState,
    screenAction: (SignupScreenAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
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
            tint = Color.White,
        )

        PasswordTextField(
            uiState = uiState,
            screenAction = screenAction,
            focusManager = focusManager,
        )

        OutlinedTextField(
            value = uiState.hint,
            onValueChange = { screenAction(SignupScreenAction.OnHintUpdate(it)) },
            label = { MandatoryText(stringResource(R.string.hint)) },
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
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GreetingPreview() {
    SafeBoxTheme {
        SignupScreen(
            uiState = SignupUiState(isSignupButtonEnabled = true),
            screenAction = { }
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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