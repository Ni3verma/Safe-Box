package com.andryoga.safebox.ui.singleRecord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.previewHelper.getAuthenticatorLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getBankAccountLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getCardLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getNoteLayoutPlan
import com.andryoga.safebox.ui.singleRecord.components.ActionButtonRow
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopAppBar
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.RowField
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.theme.SafeBoxTheme
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
    onScreenClose: () -> Unit,
) {
    val viewModel = hiltViewModel<SingleRecordViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            SingleRecordTopAppBar(
                uiState = uiState.topAppBarUiState,
                onBackClick = onScreenClose,
                onSaveClick = {
                    keyboardController?.hide()
                    viewModel.onAction(SingleRecordScreenAction.OnSaveClicked)
                },
            )
        },
    ) { innerPadding ->
        if (!uiState.isLoading) {
            SingleRecordScreen(
                uiState = uiState,
                screenAction = viewModel::onAction,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            )
        } else {
            // TODO : laoding screen.
        }
    }
}

@Composable
fun SingleRecordScreen(
    uiState: SingleRecordScreenUiState,
    screenAction: (SingleRecordScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Collapse the action row instead of removing it abruptly so the fields below glide up
        // when the user taps Edit, and settle back down after Save.
        AnimatedVisibility(
            visible = uiState.viewMode == ViewMode.VIEW,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ActionButtonRow(
                // The row stays composed, and tappable, until its collapse finishes, so a quick
                // second tap after Edit must not share or delete the record on the way into edit
                // mode.
                screenAction = { action ->
                    if (uiState.viewMode == ViewMode.VIEW) screenAction(action)
                },
            )
        }

        uiState.layoutPlan.arrangement.forEachIndexed { rowIndex, fields ->
            val rowMode = uiState.layoutPlan.rowRenderMode(fields, uiState.viewMode)
            // counting one or two cells is cheaper than memoising it, and recomputing keeps
            // the weight honest if a cell ever becomes visible based on its data.
            val visibleFields = fields.count {
                uiState.layoutPlan.fieldUiState[it.fieldId]?.isVisibleIn(rowMode) == true
            }
            // Rows whose fields exist in one mode only (the created / updated dates) collapse and
            // expand with the mode switch instead of vanishing while the other rows animate.
            AnimatedVisibility(
                visible = rowMode == uiState.viewMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    val weightOfEachField = if (visibleFields == 0) 1F else 1F / visibleFields

                    fields.forEachIndexed { columnIndex, field ->
                        val fieldUiState = uiState.layoutPlan.fieldUiState[field.fieldId]
                            ?: return@forEachIndexed

                        if (fieldUiState.isVisibleIn(rowMode)) {
                            Box(Modifier.weight(weightOfEachField)) {
                                RowField(
                                    fieldId = field.fieldId,
                                    uiState = fieldUiState,
                                    viewMode = rowMode,
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

/**
 * Mode a row of [fields] is drawn in: [viewMode] whenever the row has something to show in it.
 *
 * A row with nothing to show in [viewMode] is still composed while it collapses away, so it is
 * drawn as it looked in the first mode where it does have content (the created / updated dates
 * in view mode, an authenticator seed in create mode); otherwise the row would already be empty,
 * and therefore have no height, when the collapse starts.
 *
 * @return [viewMode], or the first mode with content when the row has none in [viewMode], or
 * [viewMode] again when no mode shows the row at all.
 */
private fun LayoutPlan.rowRenderMode(fields: List<LayoutPlan.Field>, viewMode: ViewMode): ViewMode {
    fun hasContentIn(mode: ViewMode) = fields.any { fieldUiState[it.fieldId]?.isVisibleIn(mode) == true }
    return if (hasContentIn(viewMode)) viewMode else ViewMode.entries.firstOrNull(::hasContentIn) ?: viewMode
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
