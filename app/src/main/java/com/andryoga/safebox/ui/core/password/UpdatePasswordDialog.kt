package com.andryoga.safebox.ui.core.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.MandatoryLabelText
import com.andryoga.safebox.ui.signup.PasswordValidatorState
import timber.log.Timber

/**
 * Dialog enabling the user to set a new Master Password and password hint with validation.
 *
 * @param onDismissRequest Invoked when the dialog should be dismissed.
 * @param onSave Invoked when the user submits valid new credentials (new password and hint).
 * @param modifier Modifier to be applied to the alert dialog.
 * @param onShow Invoked once when the dialog is displayed, typically for analytics logging.
 * @param onCancelClick Invoked when the user explicitly cancels or dismisses the dialog via dismiss button or back-press.
 */
@Composable
fun UpdatePasswordDialog(
    onDismissRequest: () -> Unit,
    onSave: (newPassword: String, hint: String) -> Unit,
    modifier: Modifier = Modifier,
    onShow: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        onShow()
    }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val validatorState = remember(newPassword) {
        if (newPassword.isEmpty()) {
            PasswordValidatorState.INITIAL_STATE
        } else {
            PasswordValidator.validate(newPassword)
        }
    }
    val isNewPasswordError =
        validatorState != PasswordValidatorState.PASSWORD_IS_OK && validatorState != PasswordValidatorState.INITIAL_STATE
    val isConfirmPasswordError =
        confirmPassword.isNotEmpty() && confirmPassword != newPassword
    val isSaveEnabled =
        validatorState == PasswordValidatorState.PASSWORD_IS_OK &&
                newPassword == confirmPassword &&
                hint.isNotBlank()

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onCancelClick,
        title = {
            Text(stringResource(R.string.update_password))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { MandatoryLabelText(stringResource(R.string.new_password)) },
                    placeholder = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    isError = isNewPasswordError,
                    supportingText = {
                        if (isNewPasswordError) {
                            Text(validatorState.getUiText())
                        }
                    },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image =
                            if (newPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                image,
                                contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { MandatoryLabelText(stringResource(R.string.confirm_new_password)) },
                    placeholder = { Text(stringResource(R.string.confirm_new_password)) },
                    singleLine = true,
                    isError = isConfirmPasswordError,
                    supportingText = {
                        if (isConfirmPasswordError) {
                            Text(stringResource(R.string.passwords_do_not_match))
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image =
                            if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                image,
                                contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility)
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { MandatoryLabelText(stringResource(R.string.hint)) },
                    placeholder = { Text(stringResource(R.string.enter_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Timber.i("user confirmed password update in UpdatePasswordDialog")
                    onSave(newPassword, hint)
                },
                enabled = isSaveEnabled,
            ) {
                Text(stringResource(R.string.confirm))
            }
        },

        dismissButton = {
            TextButton(onClick = onCancelClick) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        modifier = modifier
    )
}
