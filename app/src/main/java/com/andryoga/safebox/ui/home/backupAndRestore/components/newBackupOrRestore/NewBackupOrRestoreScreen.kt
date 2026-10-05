package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.InAppReviewSource
import com.andryoga.safebox.ui.core.motion.MotionTokens
import com.andryoga.safebox.ui.core.motion.fadeThrough
import com.andryoga.safebox.ui.core.motion.popInSwap
import com.andryoga.safebox.ui.core.motion.rememberRejectShake
import com.andryoga.safebox.ui.utils.findActivity
import kotlinx.coroutines.delay

@Composable
fun NewBackupOrRestoreScreen(
    operation: Operation,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<NewBackupOrRestoreVM>()
    LaunchedEffect(Unit) {
        viewModel.initVM(operation)
    }

    val uiState by viewModel.uiState.collectAsState()

    NewBackupOrRestoreDialog(
        operation = operation,
        workflowState = uiState.workflowState,
        defaultPassword = uiState.defaultPassword,
        onScreenAction = { action ->
            viewModel.onScreenAction(action)
        },
        onDismiss = onDismiss
    )

    LaunchedEffect(Unit) {
        viewModel.startReviewOnRestoreSuccess.collect {
            viewModel.inAppReviewManager.get().requestAndLaunchReview(
                activity = context.findActivity(),
                inAppReviewSource = InAppReviewSource.SUCCESSFUL_RESTORE
            )
        }
    }
}

@Composable
private fun NewBackupOrRestoreDialog(
    operation: Operation,
    workflowState: WorkflowState,
    defaultPassword: String = "",
    onScreenAction: (ScreenAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(defaultPassword) {
        mutableStateOf(defaultPassword)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            // The glyph pops in whenever the dialog changes family (prompt -> progress -> result)
            // so the outcome of a backup or restore is announced rather than silently swapped.
            AnimatedContent(
                targetState = workflowState.iconKind,
                transitionSpec = { popInSwap() },
                label = "dialogIcon",
            ) { kind ->
                DialogIcon(kind)
            }
        },
        text = {
            AnimatedContent(
                targetState = workflowState,
                contentKey = { it.bodyKey() },
                transitionSpec = { fadeThrough(withScale = false) using SizeTransform(clip = false) },
                label = "dialogBody",
            ) { state ->
                dialogBodyText(operation, state, password) { password = it }()
            }
        },
        confirmButton = confirmButtonComposable(workflowState, onScreenAction, password),
        dismissButton = cancelButtonComposable(workflowState, onDismiss),
        properties = DialogProperties(
            dismissOnClickOutside = false,
        )
    )
}

/**
 * Visual family of the dialog's status glyph. Several [WorkflowState]s share a glyph, so the icon
 * slot animates only when the family changes rather than on every state update.
 */
private enum class DialogIconKind {
    PROMPT,
    WARNING,
    PROGRESS,
    SUCCESS,
    ERROR,
}

private val WorkflowState.iconKind: DialogIconKind
    get() = when (this) {
        WorkflowState.ASK_FOR_PASSWORD -> DialogIconKind.PROMPT
        WorkflowState.WRONG_PASSWORD -> DialogIconKind.WARNING
        WorkflowState.IN_PROGRESS -> DialogIconKind.PROGRESS
        WorkflowState.SUCCESS -> DialogIconKind.SUCCESS
        WorkflowState.FAILED,
        WorkflowState.CORRUPT_FILE,
        WorkflowState.BACKUP_TOO_NEW,
        WorkflowState.BACKUP_EMPTY,
        WorkflowState.BACKUP_NOTHING_TO_BACKUP,
        WorkflowState.BACKUP_FOLDER_INACCESSIBLE,
        WorkflowState.BACKUP_WRITE_FAILED,
        WorkflowState.BACKUP_UNKNOWN_ERROR,
        -> DialogIconKind.ERROR
    }

/**
 * Identity of the dialog body for its `AnimatedContent`: the password prompt is one body whether or
 * not it is currently flagging an error, so a rejected password re-enters the same text field
 * instead of fading a fresh one in. Every other state has a body of its own.
 */
private fun WorkflowState.bodyKey(): Any = when (this) {
    WorkflowState.ASK_FOR_PASSWORD,
    WorkflowState.WRONG_PASSWORD,
    WorkflowState.FAILED,
    -> PASSWORD_BODY_KEY
    else -> this
}

private const val PASSWORD_BODY_KEY = "password"

@Composable
private fun DialogIcon(kind: DialogIconKind) {
    val modifier = Modifier.size(50.dp)
    when (kind) {
        DialogIconKind.PROMPT -> Icon(
            Icons.Filled.SettingsBackupRestore,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )

        DialogIconKind.WARNING -> Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.error
        )

        // A live indicator instead of a static "downloading" glyph: the work runs in WorkManager
        // and can take a while on a large vault, so the dialog should visibly be doing something.
        DialogIconKind.PROGRESS -> CircularProgressIndicator(
            modifier = modifier,
            strokeWidth = 4.dp,
        )

        DialogIconKind.SUCCESS -> Icon(
            Icons.Filled.CheckCircleOutline,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )

        DialogIconKind.ERROR -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun cancelButtonComposable(
    workflowState: WorkflowState,
    onDismiss: () -> Unit
): @Composable (() -> Unit) {
    return when (workflowState) {
        WorkflowState.WRONG_PASSWORD,
        WorkflowState.FAILED,
        WorkflowState.CORRUPT_FILE,
        WorkflowState.BACKUP_TOO_NEW,
        WorkflowState.BACKUP_EMPTY,
        WorkflowState.BACKUP_NOTHING_TO_BACKUP,
        WorkflowState.BACKUP_FOLDER_INACCESSIBLE,
        WorkflowState.BACKUP_WRITE_FAILED,
        WorkflowState.BACKUP_UNKNOWN_ERROR,
        WorkflowState.ASK_FOR_PASSWORD,
        WorkflowState.SUCCESS -> {
            {
                val textResId = when (workflowState) {
                    WorkflowState.SUCCESS,
                    WorkflowState.FAILED,
                    WorkflowState.CORRUPT_FILE,
                    WorkflowState.BACKUP_TOO_NEW,
                    WorkflowState.BACKUP_EMPTY,
                    WorkflowState.BACKUP_NOTHING_TO_BACKUP,
                    WorkflowState.BACKUP_FOLDER_INACCESSIBLE,
                    WorkflowState.BACKUP_WRITE_FAILED,
                    WorkflowState.BACKUP_UNKNOWN_ERROR,
                    -> R.string.common_ok
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
                        onScreenAction(ScreenAction.PasswordConfirmed(password))
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

        WorkflowState.CORRUPT_FILE -> {
            {
                Text(
                    text = stringResource(R.string.restore_corrupt_file_message),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        WorkflowState.BACKUP_TOO_NEW -> {
            {
                Text(
                    text = stringResource(R.string.restore_backup_too_new_message),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        WorkflowState.BACKUP_EMPTY -> {
            {
                Text(
                    text = stringResource(R.string.restore_backup_empty_message),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        WorkflowState.BACKUP_NOTHING_TO_BACKUP,
        WorkflowState.BACKUP_FOLDER_INACCESSIBLE,
        WorkflowState.BACKUP_WRITE_FAILED,
        WorkflowState.BACKUP_UNKNOWN_ERROR,
        -> {
            val textResId = when (workflowState) {
                WorkflowState.BACKUP_NOTHING_TO_BACKUP -> R.string.backup_nothing_to_backup_message
                WorkflowState.BACKUP_FOLDER_INACCESSIBLE -> R.string.backup_folder_inaccessible_message
                WorkflowState.BACKUP_WRITE_FAILED -> R.string.backup_write_failed_message
                else -> R.string.backup_unknown_error_message
            }
            {
                Text(
                    text = stringResource(textResId),
                    fontWeight = FontWeight.Medium,
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
    // Each rejection bumps a counter so the shake replays per attempt. The dialog body reaches
    // this state by fading through from the progress text, so the shake waits for that fade to
    // finish rather than playing on a half-transparent field.
    var rejectedAttempts by remember { mutableIntStateOf(0) }
    LaunchedEffect(workflowState) {
        if (workflowState == WorkflowState.WRONG_PASSWORD) {
            delay(MotionTokens.DURATION_MEDIUM_MS.toLong())
            rejectedAttempts++
        }
    }
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
                .then(rememberRejectShake(rejectedAttempts))
        )
    }
}