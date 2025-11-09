package com.andryoga.composeapp.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.BuildConfig
import com.andryoga.composeapp.R

@Composable
fun NewBackupOrRestoreScreen(
    operation: Operation,
    onDismiss: () -> Unit,
) {
    val viewModel = hiltViewModel<NewBackupOrRestoreVM>()
    LaunchedEffect(Unit) {
        viewModel.initVM(operation)
    }

    val uiState by viewModel.uiState.collectAsState()

    NewBackupDialog(
        operation = operation,
        workflowState = uiState.workflowState,
        onScreenAction = { action ->
            viewModel.onScreenAction(action)
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun NewBackupDialog(
    operation: Operation,
    workflowState: WorkflowState,
    onScreenAction: (ScreenAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) "Qwerty@@135" else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = dialogIconComposable(workflowState),
        text = dialogBodyText(operation, workflowState, password) { password = it },
        confirmButton = confirmButtonComposable(workflowState, onScreenAction, password),
        dismissButton = cancelButtonComposable(workflowState, onDismiss),
        properties = DialogProperties(
            dismissOnClickOutside = false,
        )
    )
}

@Composable
private fun dialogIconComposable(
    workflowState: WorkflowState,
): @Composable (() -> Unit) {
    val modifier = Modifier.size(50.dp)
    return {
        when (workflowState) {
            WorkflowState.ASK_FOR_PASSWORD -> Icon(
                Icons.Filled.SettingsBackupRestore,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.primary
            )

            WorkflowState.WRONG_PASSWORD -> Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )

            WorkflowState.IN_PROGRESS -> Icon(
                Icons.Filled.Downloading,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.primary
            )

            WorkflowState.SUCCESS -> Icon(
                Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.primary
            )

            WorkflowState.FAILED -> Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun cancelButtonComposable(
    workflowState: WorkflowState,
    onDismiss: () -> Unit
): @Composable (() -> Unit) {
    return when (workflowState) {
        WorkflowState.WRONG_PASSWORD, WorkflowState.FAILED, WorkflowState.ASK_FOR_PASSWORD, WorkflowState.SUCCESS -> {
            {
                val textResId = when (workflowState) {
                    WorkflowState.SUCCESS, WorkflowState.FAILED -> R.string.common_ok
                    else -> R.string.common_cancel
                }

                TextButton(
                    onClick = onDismiss
                ) {
                    Text(stringResource(textResId))
                }
            }
        }

        else -> {
            {}
        }
    }
}

@Composable
private fun confirmButtonComposable(
    workflowState: WorkflowState,
    onScreenAction: (ScreenAction) -> Unit,
    password: String
): @Composable (() -> Unit) {
    return when (workflowState) {
        WorkflowState.WRONG_PASSWORD, WorkflowState.ASK_FOR_PASSWORD -> {
            {
                TextButton(
                    onClick = {
                        onScreenAction(ScreenAction.ConfirmPasswordRequest(password))
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }

        else -> {
            {}
        }
    }
}

@Composable
fun dialogBodyText(
    operation: Operation,
    workflowState: WorkflowState,
    password: String,
    onPasswordChange: (String) -> Unit
): @Composable (() -> Unit) {
    return when (workflowState) {
        WorkflowState.WRONG_PASSWORD,
        WorkflowState.FAILED,
        WorkflowState.ASK_FOR_PASSWORD -> {
            {
                EnterPasswordView(
                    operation = operation,
                    workflowState = workflowState,
                    password = password,
                    onPasswordChange = onPasswordChange
                )
            }
        }

        WorkflowState.IN_PROGRESS -> {
            val textResId = when (operation) {
                Operation.Backup -> R.string.backup_in_progress_message
                is Operation.Restore -> R.string.restore_in_progress_message
            }
            {
                Text(
                    text = stringResource(textResId),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        WorkflowState.SUCCESS -> {
            val textResId = when (operation) {
                Operation.Backup -> R.string.backup_complete_message
                is Operation.Restore -> R.string.restore_complete_message
            }
            {
                Text(
                    text = stringResource(textResId),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun EnterPasswordView(
    operation: Operation,
    workflowState: WorkflowState,
    password: String,
    onPasswordChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isError =
        workflowState == WorkflowState.WRONG_PASSWORD || workflowState == WorkflowState.FAILED
    val supportingText: @Composable (() -> Unit)? = if (isError) {
        {
            Text(
                text = stringResource(
                    if (workflowState == WorkflowState.WRONG_PASSWORD) {
                        R.string.incorrect_pswrd_message
                    } else {
                        R.string.failed_message
                    }
                )
            )
        }
    } else {
        null
    }

    Column {
        val dialogTextResId = when (operation) {
            Operation.Backup -> R.string.new_backup_dialog_body_text
            is Operation.Restore -> R.string.new_restore_dialog_body_text
        }
        Text(stringResource(dialogTextResId))

        OutlinedTextField(
            value = password,
            onValueChange = { onPasswordChange(it) },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText,
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
    }
}