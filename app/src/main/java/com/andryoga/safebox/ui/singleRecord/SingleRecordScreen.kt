package com.andryoga.safebox.ui.singleRecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.previewHelper.getBankAccountLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getCardLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getNoteLayoutPlan
import com.andryoga.safebox.ui.singleRecord.components.ActionButtonRow
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarActions
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarNavIcon
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarTitle
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.RowField
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.OnStart
import timber.log.Timber

@Composable
fun SingleRecordScreenRoot(
    mainViewModel: MainViewModel,
    onScreenClose: () -> Unit,
) {
    val viewModel = hiltViewModel<SingleRecordViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    OnStart {
        val config = TopAppBarConfig(
            title = { SingleRecordTopBarTitle(uiState.topAppBarUiState.title) },
            navigationIcon = { SingleRecordTopBarNavIcon(onScreenClose) },
            actions = {
                SingleRecordTopBarActions(uiState.topAppBarUiState, onSaveClick = {
                    keyboardController?.hide()
                    viewModel.onAction(SingleRecordScreenAction.OnSaveClicked)
                })
            },
        )
        mainViewModel.updateTopBar(config)
    }

    LaunchedEffect(Unit) {
        viewModel.screenCloseEvent.collect {
            onScreenClose()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareContentEvent.collect { dataToShare ->
            Timber.i("starting intent to share data")
            viewModel.activeSessionManager.get().setPaused(true)
            ShareCompat.IntentBuilder(context)
                .setType("text/plain")
                .setText(dataToShare)
                .startChooser()
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
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
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
