package com.andryoga.safebox.ui.singleRecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.OnStart
import timber.log.Timber

/**
 * This is a common screen for different use cases.
 * 1. View a single record - VIEW MODE
 * 2. Edit a single record - EDIT (update) a record (user can edit a record only after
 * clicking edit button on view mode screen)
 * 3. Create a new record - Add a new record.
 *
 * Use remember {} carefully in this screen as something that you cached in view mode might create
 * a problem in edit mode. Most of the times uiState.viewMode will be a good candidate for the
 * key of remember(uiState.viewMode){}
 * */
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
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
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
                val weightOfEachField = remember(uiState.viewMode) {
                    if (uiState.viewMode == ViewMode.VIEW) {
                        val nonEmptyFields = fields.count {
                            val fieldUiState = uiState.layoutPlan.fieldUiState[it.fieldId]
                            fieldUiState != null && fieldUiState.data.isNotBlank()
                        }
                        if (nonEmptyFields == 0) {
                            // all the fields of row are empty in view mode, so we will not show
                            // anything on the screen. so we can return 1F here.
                            1F
                        } else {
                            1F / nonEmptyFields
                        }
                    } else {
                        1F / fields.size
                    }
                }

                fields.forEachIndexed { columnIndex, field ->
                    val fieldUiState = uiState.layoutPlan.fieldUiState[field.fieldId]!!
                    val isVisibleOnlyInViewMode = fieldUiState.cell.isVisibleOnlyInViewMode

                    /**
                     * two conditions to show a field:
                     * 1. we have opened the screen in view mode. i.e. user clicked on a saved record.
                     * in this case all of the non-empty fields should be visible including fields such as creation date
                     *
                     * 2. field is "NOT" visible "only" in view mode. e.g. title field.
                     * i.e we can edit it and it was entered by user while saving
                     * some fields like creationDate are only visible in View mode.
                     * */

                    if (uiState.viewMode == ViewMode.VIEW && fieldUiState.data.isNotBlank()) {
                        // we are in view mode and the data for the field is not blank, so we can show it
                        Box(Modifier.weight(weightOfEachField)) {
                            RowField(
                                fieldId = field.fieldId,
                                uiState = fieldUiState,
                                viewMode = uiState.viewMode,
                                screenAction = screenAction
                            )
                        }
                    } else if (uiState.viewMode != ViewMode.VIEW && !isVisibleOnlyInViewMode) {
                        // we are in edit mode/ or new record mode, so all the fields should be
                        // shown except for the one that should be shown in only view mode
                        Box(Modifier.weight(weightOfEachField)) {
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

@LightDarkModePreview
@Composable
private fun SingleRecordScreenCardWithSomeFieldsReadOnlyPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layoutPlan = getCardLayoutPlan(withData = true, emptyFields = listOf(FieldId.CARD_CVV)),
            viewMode = ViewMode.VIEW
        ),
        {}
    )
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenCardWithSomeFields2ReadOnlyPreview() {
    SingleRecordScreen(
        SingleRecordScreenUiState(
            isLoading = false,
            layoutPlan = getCardLayoutPlan(withData = true, emptyFields = listOf(FieldId.CARD_PIN)),
            viewMode = ViewMode.VIEW
        ),
        {}
    )
}
