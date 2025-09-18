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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.RowField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleRecordScreenRoot(
    setTopBar: ((@Composable () -> Unit)?) -> Unit,
    onScreenClose: () -> Unit
) {
    val viewModel = hiltViewModel<SingleRecordViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        setTopBar {
            TopAppBar(
                title = { Text("Add new record") },
                navigationIcon = {
                    IconButton(onClick = {
                        setTopBar(null)
                        onScreenClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
//                            viewModel.onSaveClicked()
                            onScreenClose()
                        },
                        enabled = uiState.isSaveEnabled
                    ) {
                        Text("SAVE")
                    }
                }
            )
        }
    }

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
                .padding(horizontal = 20.dp)
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
        ),
        {}
    )
}

@Preview
@Composable
private fun SingleRecordScreenBankAccountPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getBankAccountRecordLayout()
        ),
        {}
    )
}

@Preview
@Composable
private fun SingleRecordScreenCardPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getCardRecordLayout()
        ),
        {}
    )
}

@Preview
@Composable
private fun SingleRecordScreenNotePreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layout = LayoutFactory.getNoteRecordLayout()
        ),
        {}
    )
}
