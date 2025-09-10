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

@Composable
fun RowField(
    rowIndex: Int,
    columnIndex: Int,
    field: Layout.Field,
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = field.uiState.data,
        onValueChange = {
            screenAction(
                SingleRecordScreenAction.onCellValueUdate(
                    data = it,
                    rowIndex = rowIndex,
                    columnIndex = columnIndex
                )
            )
        },
        label = {
            if (field.uiState.cell.isMandatory) {
                MandatoryLabelText(text = stringResource(field.uiState.cell.label))
            } else {
                Text(
                    text = stringResource(field.uiState.cell.label),
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
            }
        },
        singleLine = field.uiState.cell.singleLine,
        minLines = field.uiState.cell.minLines,
        visualTransformation = if (field.uiState.cell.isPasswordField && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.Companion.None
        },
        trailingIcon = {
            if (field.uiState.cell.isPasswordField) {
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
            keyboardType = field.uiState.cell.keyboardType
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
        rowIndex = 0,
        columnIndex = 0,
        field = Layout.Field(
            uiState = Layout.Field.UiState(
                cell = Layout.Field.UiState.Cell(
                    label = R.string.title,
                    isMandatory = true
                )
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun NonMandatoryRowFieldPreview() {
    RowField(
        rowIndex = 0,
        columnIndex = 0,
        field = Layout.Field(
            uiState = Layout.Field.UiState(
                cell = Layout.Field.UiState.Cell(
                    label = R.string.title,
                )
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun PasswordRowFieldPreview() {
    RowField(
        rowIndex = 0,
        columnIndex = 0,
        field = Layout.Field(
            uiState = Layout.Field.UiState(
                cell = Layout.Field.UiState.Cell(
                    label = R.string.title,
                    isPasswordField = true
                )
            )
        ),
        screenAction = {}
    )
}

@Preview
@Composable
private fun BigRowFieldPreview() {
    RowField(
        rowIndex = 0,
        columnIndex = 0,
        field = Layout.Field(
            uiState = Layout.Field.UiState(
                cell = Layout.Field.UiState.Cell(
                    label = R.string.title,
                    minLines = 5,
                    singleLine = false
                )
            )
        ),
        screenAction = {}
    )
}