package com.andryoga.composeapp.ui.singleRecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.RowField

@Composable
fun SingleRecordScreenRoot() {
    val viewModel = hiltViewModel<SingleRecordViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isLoading) {
        SingleRecordScreen(
            uiState = uiState,
            screenAction = viewModel::onAction
        )
    } else {
        // TODO : laoding screen.
    }
}

@Composable
fun SingleRecordScreen(
    uiState: SingleRecordScreenUiState,
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            uiState.layout.arrangement.forEachIndexed { rowIndex, fields ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    fields.forEachIndexed { columnIndex, field ->
                        Box(Modifier.weight(field.weight)) {
                            RowField(
                                fieldId = field.fieldId,
                                uiState = uiState.layout.fieldUiState[field.fieldId]!!,
                                screenAction = screenAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SingleRecordScreenLoginPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getLoginRecordLayout()
        ), {})
}

@Preview
@Composable
private fun SingleRecordScreenBankAccountPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getBankAccountRecordLayout()
        ), {})
}

@Preview
@Composable
private fun SingleRecordScreenCardPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getCardRecordLayout()
        ), {})
}

@Preview
@Composable
private fun SingleRecordScreenNotePreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getNoteRecordLayout()
        ), {})
}

@Preview
@Composable
private fun SingleRecordScreenLoadingPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = true,
        ), {})
}