package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.andryoga.safebox.R

/**
 * The change-password gate when the screen lock cannot be used: the current master password,
 * checked by the ViewModel against the vault's password wrap.
 *
 * @param isIncorrect The last submitted password was wrong; shown under the field.
 * @param isVerifying A check is in flight; submit is disabled meanwhile.
 * @param onSubmit Invoked with the entered password.
 * @param onDismiss Invoked on Cancel or a dismissal outside the dialog.
 * @param onShow Invoked once when the dialog enters the composition, for analytics.
 */
@Composable
internal fun CurrentPasswordDialog(
    isIncorrect: Boolean,
    isVerifying: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    onShow: () -> Unit,
) {
    LaunchedEffect(Unit) { onShow() }
    // Kept out of saved state: a password must not be written to the saved-instance bundle.
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val canSubmit = password.isNotEmpty() && !isVerifying

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_current_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.settings_current_password_label)) },
                singleLine = true,
                isError = isIncorrect,
                supportingText = {
                    if (isIncorrect) Text(stringResource(R.string.incorrect_pswrd_message))
                },
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(password) }),
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }, enabled = canSubmit) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
