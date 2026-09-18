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
import com.andryoga.safebox.ui.previewHelper.getAuthenticatorLayoutPlan
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
                    val visibleFields = fields.count {
                        val fieldUiState = uiState.layoutPlan.fieldUiState[it.fieldId]
                        fieldUiState != null && fieldUiState.isVisibleIn(uiState.viewMode)
                    }
                    if (visibleFields == 0) {
                        // the whole row is hidden, so nothing is laid out and the weight is
                        // never actually used.
                        1F
                    } else {
                        1F / visibleFields
                    }
                }

                fields.forEachIndexed { columnIndex, field ->
                    val fieldUiState = uiState.layoutPlan.fieldUiState[field.fieldId]!!

                    if (fieldUiState.isVisibleIn(uiState.viewMode)) {
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

@LightDarkModePreview
@Composable
private fun SingleRecordScreenAuthenticatorPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getAuthenticatorLayoutPlan()
            ),
            {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SingleRecordScreenAuthenticatorReadOnlyPreview() {
    SafeBoxTheme {
        SingleRecordScreen(
            SingleRecordScreenUiState(
                isLoading = false,
                layoutPlan = getAuthenticatorLayoutPlan(withData = true),
                viewMode = ViewMode.VIEW
            ),
            {}
        )
    }
}
