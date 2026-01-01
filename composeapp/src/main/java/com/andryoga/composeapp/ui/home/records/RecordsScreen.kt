@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.composeapp.ui.home.records

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.R
import com.andryoga.composeapp.domain.models.record.RecordType
import com.andryoga.composeapp.ui.MainViewModel
import com.andryoga.composeapp.ui.core.ScrollBehaviorType
import com.andryoga.composeapp.ui.core.TopAppBarConfig
import com.andryoga.composeapp.ui.home.records.components.AddNewRecordBottomSheet
import com.andryoga.composeapp.ui.home.records.components.NotificationPermissionRationaleDialog
import com.andryoga.composeapp.ui.home.records.components.RecordItem
import com.andryoga.composeapp.ui.home.records.components.RecordTypeFilterRow
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarActions
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarNavIcon
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarTitle
import com.andryoga.composeapp.ui.home.records.components.shouldShowNotificationPermissionRationaleDialog
import com.andryoga.composeapp.ui.home.records.models.NotificationPermissionState
import com.andryoga.composeapp.ui.home.records.models.RecordsState
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.previewHelper.getRecordState
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import com.andryoga.composeapp.ui.utils.OnStart
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import timber.log.Timber

@Composable
fun RecordsScreenRoot(
    mainViewModel: MainViewModel,
    onAddNewRecord: (RecordType) -> Unit,
    onRestoreFromBackup: () -> Unit,
    onRecordClick: (id: Int, recordType: RecordType) -> Unit,
) {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val recordState by viewModel.recordState.collectAsState()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsState()

    OnStart {
        val config = TopAppBarConfig(
            title = { RecordsSearchBarTitle(uiState.searchText, viewModel::onScreenAction) },
            navigationIcon = { RecordsSearchBarNavIcon() },
            actions = { RecordsSearchBarActions(uiState.searchText, viewModel::onScreenAction) },
            scrollBehaviorType = ScrollBehaviorType.ENTER_ALWAYS
        )
        mainViewModel.updateTopBar(config)
    }

    RecordsScreen(
        uiState = uiState,
        notificationPermissionState = notificationPermissionState,
        recordState = recordState,
        onRestoreFromBackup = onRestoreFromBackup,
        onScreenAction = { action ->
            when (action) {
                is RecordScreenAction.OnAddNewRecord -> onAddNewRecord(action.recordType)
                is RecordScreenAction.OnRecordClick -> onRecordClick(action.id, action.recordType)
                else -> viewModel.onScreenAction(action)
            }
        },
    )
}

@Composable
private fun RecordsScreen(
    uiState: RecordsUiState,
    notificationPermissionState: NotificationPermissionState,
    recordState: RecordsState,
    onRestoreFromBackup: () -> Unit,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    if (uiState.isLoading) {
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
    } else if (recordState.records.isEmpty() && recordState.totalDbRecords == 0) {
        // user has added no record and probably this is the first time he has logged in
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            DotLottieAnimation(
                source = DotLottieSource.Asset("ghost.lottie"),
                autoplay = true,
                loop = true,
                useFrameInterpolation = false,
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

    } else if (recordState.records.isEmpty() && recordState.totalDbRecords > 0) {
        // user has some records in db but has also applied some filters because pf which nothing can be displayed
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterRow(uiState, onScreenAction)
            DotLottieAnimation(
                source = DotLottieSource.Asset("ghost.lottie"),
                autoplay = true,
                loop = true,
                useFrameInterpolation = false,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = stringResource(R.string.no_filtered_record_title),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = stringResource(R.string.no_filtered_record_body),
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    } else {
        val records = recordState.records
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterRow(uiState, onScreenAction) }
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
                    }
                )
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
                onPermissionAskedFirstTime = {
                    // update is notification asked before in pref
                    showNotificationPermissionRationaleDialog = false
                    onScreenAction(RecordScreenAction.OnNotificationPermissionAskedForFirstTime)
                },
                onCancelClick = { neverAsk ->
                    showNotificationPermissionRationaleDialog = false
                    Timber.i("notification permission rationale dialog cancelled, never ask = $neverAsk")
                    if (neverAsk) {
                        onScreenAction(RecordScreenAction.OnNeverAskForNotificationPermission)
                    }
                },
                dismissDialogAction = {
                    showNotificationPermissionRationaleDialog = false
                }
            )
        }
    }

    if (uiState.isShowAddNewRecordsBottomSheet) {
        AddNewRecordBottomSheet(
            onDismiss = {
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false
                    )
                )
            },
            onAddNewRecord = {
                onScreenAction(RecordScreenAction.OnAddNewRecord(it))
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false
                    )
                )
            }
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
            recordState = getRecordState(),
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
            recordState = getRecordState(),
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
            uiState = RecordsUiState(isLoading = true),
            notificationPermissionState = NotificationPermissionState(),
            recordState = RecordsState(),
            onRestoreFromBackup = {},
            onScreenAction = {},
        )
    }
}
