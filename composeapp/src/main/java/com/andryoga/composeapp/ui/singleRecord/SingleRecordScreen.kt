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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.previewHelper.getBankAccountLayoutPlan
import com.andryoga.composeapp.ui.previewHelper.getCardLayoutPlan
import com.andryoga.composeapp.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.composeapp.ui.previewHelper.getNoteLayoutPlan
import com.andryoga.composeapp.ui.singleRecord.components.ActionButtonRow
import com.andryoga.composeapp.ui.singleRecord.components.TopBar
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.RowField
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.composeapp.ui.theme.SafeBoxTheme

@Composable
fun SingleRecordScreenRoot(
    setTopBar: ((@Composable () -> Unit)?) -> Unit,
    onScreenClose: () -> Unit
) {
    val viewModel = hiltViewModel<SingleRecordViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.topAppBarUiState) {
        setTopBar {
            TopBar(
                uiState = uiState.topAppBarUiState,
                onBackClick = viewModel::onBackClick,
                onSaveClick = {
                    viewModel.onAction(SingleRecordScreenAction.OnSaveClicked)
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.screenCloseEvent.collect {
            onScreenClose()
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
            if (uiState.viewMode == ViewMode.VIEW) {
                ActionButtonRow(
                    screenAction = screenAction
                )
            }

            uiState.layoutPlan.arrangement.forEachIndexed { rowIndex, fields ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    fields.forEachIndexed { columnIndex, field ->
                        val fieldUiState = uiState.layoutPlan.fieldUiState[field.fieldId]!!
                        val isVisibleOnlyInViewMode = fieldUiState.cell.isVisibleOnlyInViewMode

                        /**
                         * two conditions to show the field:
                         * 1. field is not visible "only" in view mode. e.g. title field.
                         * i.e we can edit it and it was entered by user while saving
                         *
                         * 2. we have opened the screen in view mode. i.e. user clicked on a saved record.
                         * in this case all of the fields should be visible including fields such as creation date
                         * */

                        if (!isVisibleOnlyInViewMode || uiState.viewMode == ViewMode.VIEW) {
                            Box(Modifier.weight(field.weight)) {
                                RowField(
                                    fieldId = field.fieldId,
                                    uiState = fieldUiState,
                                    viewMode = uiState.viewMode,
                                    screenAction = screenAction
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenLoginPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getLoginLayoutPlan()
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenBankAccountPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getBankAccountLayoutPlan()
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenCardPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getCardLayoutPlan()
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenNotePreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getNoteLayoutPlan()
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenNoteReadOnlyPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getNoteLayoutPlan(withData = true),
                viewMode = ViewMode.VIEW
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenLoginReadOnlyPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getLoginLayoutPlan(withData = true),
                viewMode = ViewMode.VIEW
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenBankAccountReadOnlyPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getBankAccountLayoutPlan(withData = true),
                viewMode = ViewMode.VIEW
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenCardReadOnlyPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getCardLayoutPlan(withData = true),
                viewMode = ViewMode.VIEW
            ),
            {}
        )
    }
}
