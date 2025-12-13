package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.LocalSnackbarHostState
import com.andryoga.composeapp.ui.core.MandatoryLabelText
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenAction
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun RowField(
    fieldId: FieldId,
    uiState: FieldUiState,
    viewMode: ViewMode,
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var isPasswordVisible by remember { mutableStateOf(false) }
    val snackbarHost = LocalSnackbarHostState.current

    if (viewMode == ViewMode.VIEW) {
        if (uiState.data.isNotBlank()) {
            val label = stringResource(uiState.cell.label)
            Column {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = uiState.data,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable {
                            scope.launch {
                                Timber.i("setting clip entry for $label")
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            label,
                                            uiState.data
                                        )
                                    )
                                )

                                // Android 13 (Tiramisu) introduced the system-level clipboard overlay.
                                // so need to show our own snackbar
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    snackbarHost.currentSnackbarData?.dismiss()
                                    snackbarHost.showSnackbar(
                                        message = "Copied $label to clipboard",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                )
            }
        }
    } else {
        OutlinedTextField(
            value = uiState.data,
            onValueChange = {
                screenAction(
                    SingleRecordScreenAction.OnCellValueUpdate(
                        fieldId = fieldId,
                        data = it,
                    )
                )
            },
            label = {
                if (uiState.cell.isMandatory) {
                    MandatoryLabelText(text = stringResource(uiState.cell.label))
                } else {
                    Text(
                        text = stringResource(uiState.cell.label),
                        maxLines = 1,
                        overflow = TextOverflow.Companion.Ellipsis
                    )
                }
            },
            singleLine = uiState.cell.singleLine,
            minLines = uiState.cell.minLines,
            visualTransformation = if (uiState.cell.isPasswordField && !isPasswordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.Companion.None
            },
            trailingIcon = {
                if (uiState.cell.isPasswordField) {
                    val image =
                        if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            image,
                            contentDescription = stringResource(R.string.cd_toggle_sensitive_data_visibility)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = uiState.cell.keyboardType
            ),
            modifier = Modifier.Companion
                .padding(top = 16.dp)
                .fillMaxWidth()
        )
    }
}

@LightDarkModePreview
@Composable
private fun MandatoryRowFieldPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                    isMandatory = true
                )
            ),
            viewMode = ViewMode.NEW,
            screenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun NonMandatoryRowFieldPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                )
            ),
            viewMode = ViewMode.NEW,
            screenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun PasswordRowFieldPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                    isPasswordField = true
                )
            ),
            viewMode = ViewMode.NEW,
            screenAction = {}
        )
    }
}

// this preview should not show anything because I am padding empty data here
@LightDarkModePreview
@Composable
private fun BigRowFieldPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                    minLines = 5,
                    singleLine = false
                )
            ),
            viewMode = ViewMode.NEW,
            screenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun ViewOnlyRowFieldPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                ),
                data = "hello"
            ),
            viewMode = ViewMode.VIEW,
            screenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun ViewOnlyRowFieldWithNoDataPreview() {
    SafeBoxTheme {
        RowField(
            fieldId = FieldId.UNKNOWN,
            uiState = FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title,
                ),
                data = ""
            ),
            viewMode = ViewMode.VIEW,
            screenAction = {}
        )
    }
}