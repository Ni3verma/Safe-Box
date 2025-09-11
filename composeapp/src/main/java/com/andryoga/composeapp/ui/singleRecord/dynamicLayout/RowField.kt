package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.MandatoryLabelText
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenAction
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState

@Composable
fun RowField(
    fieldId: FieldId,
    uiState: FieldUiState,
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

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

@Preview
@Composable
private fun MandatoryRowFieldPreview() {
    RowField(
        fieldId = FieldId.UNKNOWN,
        uiState = FieldUiState(
            cell = FieldUiState.Cell(
                label = R.string.title,
                isMandatory = true
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun NonMandatoryRowFieldPreview() {
    RowField(
        fieldId = FieldId.UNKNOWN,
        uiState = FieldUiState(
            cell = FieldUiState.Cell(
                label = R.string.title,
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun PasswordRowFieldPreview() {
    RowField(
        fieldId = FieldId.UNKNOWN,
        uiState = FieldUiState(
            cell = FieldUiState.Cell(
                label = R.string.title,
                isPasswordField = true
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun BigRowFieldPreview() {
    RowField(
        fieldId = FieldId.UNKNOWN,
        uiState = FieldUiState(
            cell = FieldUiState.Cell(
                label = R.string.title,
                minLines = 5,
                singleLine = false
            )
        ),
        screenAction = {}
    )
}