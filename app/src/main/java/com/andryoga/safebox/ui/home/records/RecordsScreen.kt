@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.safebox.ui.home.records

import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.core.InAppReviewSource
import com.andryoga.safebox.ui.core.TestTags
import com.andryoga.safebox.ui.core.motion.fadeThrough
import com.andryoga.safebox.ui.core.motion.incomingSpec
import com.andryoga.safebox.ui.core.motion.outgoingSpec
import com.andryoga.safebox.ui.home.records.components.AddNewRecordBottomSheet
import com.andryoga.safebox.ui.home.records.components.NotificationPermissionRationaleDialog
import com.andryoga.safebox.ui.home.records.components.RecordItem
import com.andryoga.safebox.ui.home.records.components.RecordTypeFilterRow
import com.andryoga.safebox.ui.home.records.components.RecordsTopAppBar
import com.andryoga.safebox.ui.home.records.components.shouldShowNotificationPermissionRationaleDialog
import com.andryoga.safebox.ui.home.records.models.NotificationPermissionState
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.previewHelper.getAppliedRecordTypeFilters
import com.andryoga.safebox.ui.previewHelper.getAuthenticatorRecordItem
import com.andryoga.safebox.ui.previewHelper.getBankAccountRecordItem
import com.andryoga.safebox.ui.previewHelper.getCardRecordItem
import com.andryoga.safebox.ui.previewHelper.getLoginRecordItem
import com.andryoga.safebox.ui.previewHelper.getNoteRecordItem
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.findActivity
import timber.log.Timber

@Composable
fun RecordsScreenRoot(
    onAddNewRecord: (RecordType) -> Unit,
    onRestoreFromBackup: () -> Unit,
    onRecordClick: (id: Int, recordType: RecordType) -> Unit,
) {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.startInAppReview.collect {
            viewModel.inAppReviewManager.get().requestAndLaunchReview(
                activity = context.findActivity(),
                inAppReviewSource = InAppReviewSource.AFTER_X_LOGINS
            )
        }
    }

    Scaffold(
        topBar = {
            RecordsTopAppBar(
                searchTextState = viewModel.searchTextState,
                onScreenAction = viewModel::onScreenAction,
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        RecordsScreen(
            uiState = uiState,
            notificationPermissionState = notificationPermissionState,
            onRestoreFromBackup = onRestoreFromBackup,
            onScreenAction = { action ->
                when (action) {
                    is RecordScreenAction.OnAddNewRecord -> {
                        Timber.i("add new record action")
                        onAddNewRecord(action.recordType)
                    }

                    is RecordScreenAction.OnRecordClick -> {
                        Timber.i("record click action")
                        onRecordClick(action.id, action.recordType)
                    }
                    else -> viewModel.onScreenAction(action)
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@VisibleForTesting
@Composable
internal fun RecordsScreen(
    uiState: RecordsUiState,
    notificationPermissionState: NotificationPermissionState,
    onRestoreFromBackup: () -> Unit,
    onScreenAction: (RecordScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        RecordsContent(
            uiState = uiState,
            notificationPermissionState = notificationPermissionState,
            onRestoreFromBackup = onRestoreFromBackup,
            onScreenAction = onScreenAction,
        )
    }

    if (uiState.isShowAddNewRecordsBottomSheet) {
        AddNewRecordBottomSheet(
            onDismiss = {
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false,
                    )
                )
            },
            onAddNewRecord = {
                onScreenAction(RecordScreenAction.OnAddNewRecord(it))
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false,
                    )
                )
            }
        )
    }
}

/**
 * The body of the records screen: loader, one of the two empty states, or the filtered list.
 * Split out of [RecordsScreen] only so the scaffold padding can be applied to it as a unit while
 * the bottom sheet, which is a window of its own, stays outside the padded area.
 *
 * The three layouts fade through each other. The "nothing matches the filters" message is an
 * item of the list itself rather than a fourth layout, so when a filter empties the list the
 * chips stay put while the rows animate out and the message animates in.
 */
@Composable
private fun RecordsContent(
    uiState: RecordsUiState,
    notificationPermissionState: NotificationPermissionState,
    onRestoreFromBackup: () -> Unit,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    AnimatedContent(
        targetState = uiState.body(),
        transitionSpec = { fadeThrough() },
        label = "recordsBody",
    ) { body ->
        when (body) {
            RecordsBody.LOADING -> LoadingBody()
            RecordsBody.EMPTY_VAULT -> EmptyVaultBody(
                onRestoreFromBackup = onRestoreFromBackup,
                onScreenAction = onScreenAction,
            )
            RecordsBody.LIST -> RecordsListBody(
                uiState = uiState,
                notificationPermissionState = notificationPermissionState,
                onScreenAction = onScreenAction,
            )
        }
    }
}

/** Which of the records layouts is on screen; the key the body fade through animates between. */
private enum class RecordsBody { LOADING, EMPTY_VAULT, LIST }

private fun RecordsUiState.body(): RecordsBody = when {
    isLoading -> RecordsBody.LOADING
    // user has added no record and probably this is the first time he has logged in
    records.isEmpty() && totalDbRecords == 0 -> RecordsBody.EMPTY_VAULT
    else -> RecordsBody.LIST
}

private const val FILTER_ROW_KEY = "filter_row"
private const val NO_FILTERED_RECORDS_KEY = "no_filtered_records"

/**
 * `animateItem` with fade through timing: rows that leave clear out quickly before rows that
 * arrive fade in, so a filter change never crossfades the old rows on top of the new ones.
 * Placement keeps the default spring so surviving rows glide into their new slots.
 *
 * @param itemScope The `LazyItemScope` of the item being composed; `animateItem` only exists there.
 */
private fun Modifier.fadeThroughItem(itemScope: LazyItemScope): Modifier = with(itemScope) {
    animateItem(
        fadeInSpec = incomingSpec(),
        fadeOutSpec = outgoingSpec(),
    )
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading_data),
            fontSize = 24.sp,
            modifier = Modifier
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun EmptyVaultBody(
    onRestoreFromBackup: () -> Unit,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.no_record),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = {
            onScreenAction(
                RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                    showAddNewRecordBottomSheet = true
                )
            )
        }) {
            Text(stringResource(R.string.new_record_button))
        }
        Button(onClick = {
            onRestoreFromBackup()
        }) {
            Text(stringResource(R.string.restore_records_button))
        }
    }
}

@Composable
private fun RecordsListBody(
    uiState: RecordsUiState,
    notificationPermissionState: NotificationPermissionState,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    val records = uiState.records
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.RECORDS_LIST),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = FILTER_ROW_KEY) {
            FilterRow(uiState, onScreenAction, modifier = Modifier.fadeThroughItem(this))
        }
        if (records.isEmpty()) {
            // user has some records in db but has also applied some filters because pf which nothing can be displayed
            item(key = NO_FILTERED_RECORDS_KEY) {
                NoFilteredRecords(modifier = Modifier.fadeThroughItem(this))
            }
        } else {
            items(
                items = records,
                key = { it.key }
            ) { record ->
                RecordItem(
                    item = record,
                    onRecordClick = { id, recordType ->
                        onScreenAction(
                            RecordScreenAction.OnRecordClick(id, recordType)
                        )
                    },
                    onCopyTotpCode = {
                        onScreenAction(RecordScreenAction.OnCopyTotpCode)
                    },
                    modifier = Modifier.fadeThroughItem(this),
                )
            }
        }
    }

    var showNotificationPermissionRationaleDialog by rememberSaveable { mutableStateOf(true) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shouldShowNotificationPermissionRationaleDialog(
            showNotificationPermissionRationaleDialog,
            notificationPermissionState,
            LocalContext.current
        )
    ) {
        NotificationPermissionRationaleDialog(
            isNotificationPermissionAskedBefore = notificationPermissionState.isNotificationPermissionAskedBefore,
            onAllowClick = { isRedirectingToSettings ->
                // update is notification asked before in pref
                showNotificationPermissionRationaleDialog = false
                onScreenAction(
                    RecordScreenAction.OnNotificationAllowedFromRationaleDialog(
                        isRedirectingToSettingsPage = isRedirectingToSettings
                    )
                )
            },
            onCancelClick = { neverAsk ->
                showNotificationPermissionRationaleDialog = false
                Timber.i("notification permission rationale dialog cancelled, never ask = $neverAsk")
                onScreenAction(
                    RecordScreenAction.OnCancelClickFromRationaleDialog(
                        neverAsk = neverAsk
                    )
                )
            },
            dismissDialogAction = {
                showNotificationPermissionRationaleDialog = false
            }
        )
    }
}

@Composable
private fun NoFilteredRecords(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 64.dp)
                .size(72.dp)
        )
        Text(
            text = stringResource(R.string.no_filtered_record_title),
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.no_filtered_record_body),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun FilterRow(
    uiState: RecordsUiState,
    onScreenAction: (RecordScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    RecordTypeFilterRow(
        filters = uiState.recordTypeFilters,
        onFilterToggle = {
            onScreenAction(
                RecordScreenAction.OnToggleRecordTypeFilter(recordType = it)
            )
        },
        modifier = modifier
    )
}

@LightDarkModePreview
@Composable
private fun RecordsScreenPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecordsScreenWithNoRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecordsScreenWithNoFilteredRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false, totalDbRecords = 2),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecordsScreenWithAddNewRecordBottomSheetPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                isLoading = false,
                isShowAddNewRecordsBottomSheet = true
            ),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenLoadingRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenWithRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                isLoading = false,
                records = listOf(
                    getLoginRecordItem(),
                    getCardRecordItem(),
                    getNoteRecordItem(),
                    getBankAccountRecordItem(),
                    getAuthenticatorRecordItem(),
                ),
                totalDbRecords = 5
            ),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenWithFilteredRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                isLoading = false,
                records = listOf(
                    getLoginRecordItem()
                ),
                totalDbRecords = 4,
                recordTypeFilters = getAppliedRecordTypeFilters(),
            ),
            notificationPermissionState = NotificationPermissionState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}
