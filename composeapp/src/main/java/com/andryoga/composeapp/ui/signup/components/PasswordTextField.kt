package com.andryoga.composeapp.ui.signup.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.ui.core.MandatoryText
import com.andryoga.composeapp.ui.signup.SignupScreenAction
import com.andryoga.composeapp.ui.signup.SignupUiState

@Composable
fun PasswordTextField(
    uiState: SignupUiState,
    screenAction: (SignupScreenAction.OnPasswordUpdate) -> Unit,
    focusManager: FocusManager,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = uiState.password,
        onValueChange = { screenAction(SignupScreenAction.OnPasswordUpdate(it)) },
        label = { MandatoryText("Password") },
        placeholder = { Text("Enter a strong password") },
        singleLine = true,
        isError = uiState.isPasswordFieldError,
        supportingText = {
            if (uiState.isPasswordFieldError) {
                Text(uiState.passwordValidatorState.getUiText())
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image =
                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(image, contentDescription = "Toggle Password")
            }
        },
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
    )
}