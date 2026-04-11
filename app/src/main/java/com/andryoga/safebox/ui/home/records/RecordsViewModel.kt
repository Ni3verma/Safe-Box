package com.andryoga.safebox.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.mappers.record.toRecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.andryoga.safebox.ui.home.records.models.NotificationPermissionState
import com.andryoga.safebox.ui.home.records.models.UserInputs
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    bankAccountDataRepository: BankAccountDataRepository,
    secureNoteDataRepository: SecureNoteDataRepository,
    loginDataRepository: LoginDataRepository,
    cardDataRepository: BankCardDataRepository,
    dispatchersProvider: DispatchersProvider,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val preferenceProvider: PreferenceProvider,
    private val analyticsHelper: AnalyticsHelper,
    val inAppReviewManager: Lazy<InAppReviewManager>,
) : ViewModel() {
    private val _notificationPermissionState = MutableStateFlow(
        NotificationPermissionState()
    )
    val notificationPermissionState: StateFlow<NotificationPermissionState> =
        _notificationPermissionState.onStart { loadNotificationPermissionState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = _notificationPermissionState.value
            )

    /**
     * Start In-App review flow if user logs in after x times again.
     */
    val startInAppReview: SharedFlow<Unit> = flow {
        val totalLoginCount =
            preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
        if (totalLoginCount % ASK_FOR_REVIEW_AFTER_EVERY_LOGIN == 0) {
            emit(Unit)
        }
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    private val dbRecords = combine(
        bankAccountDataRepository.getAllBankAccountData(),
        secureNoteDataRepository.getAllSecureNoteData(),
        loginDataRepository.getAllLoginData(),
        cardDataRepository.getAllBankCardData()
    ) { bankAccountData, secureNoteData, loginData, cardData ->
        val combinedList = bankAccountData.map { it.toRecordListItem() } +
                secureNoteData.map { it.toRecordListItem() } +
                loginData.map { it.toRecordListItem() } +
                cardData.map { it.toRecordListItem() }
        combinedList.sortedBy { it.title.lowercase() }
    }
        .flowOn(dispatchersProvider.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val userInputs = MutableStateFlow(UserInputs())

    val uiState = combine(dbRecords, userInputs) { dbRecords, userInputs ->
        if (dbRecords == null) {
            RecordsUiState()
        } else {
            var filteredRecords = dbRecords
            val totalRecordsInDb = dbRecords.size
            filteredRecords = dbRecords.filter {
                it.title.contains(
                    userInputs.searchText,
                    ignoreCase = true
                )
            }

            val selectedFilters = userInputs.recordTypeFilters.filter { it.isSelected }
            if (selectedFilters.isNotEmpty()) {
                val recordTypeToFilterOn = selectedFilters.map { it.recordType }
                filteredRecords = filteredRecords.filter { it.recordType in recordTypeToFilterOn }
            }

            RecordsUiState(
                isLoading = false,
                isShowAddNewRecordsBottomSheet = userInputs.isAddNewRecordBottomSheetVisible,
                searchText = userInputs.searchText,
                recordTypeFilters = userInputs.recordTypeFilters,
                records = filteredRecords,
                totalDbRecords = totalRecordsInDb,
            )
        }
    }.flowOn(dispatchersProvider.default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordsUiState()
    )

    private suspend fun loadNotificationPermissionState() {
        _notificationPermissionState.update {
            it.copy(
                isNotificationPermissionAskedBefore = preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    false
                ),
                isNeverAskForNotificationPermission = preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    false
                ),
                isBackupPathSet = backupMetadataRepository.isBackupPathSet()
            )
        }

    }

    fun onScreenAction(action: RecordScreenAction) {
        Timber.i("on screen action: ${action::class.simpleName}")
        when (action) {
            is RecordScreenAction.OnSearchTextUpdate -> {
                onSearchTextUpdate(searchText = action.searchText)
            }

            is RecordScreenAction.OnToggleRecordTypeFilter -> {
                onToggleRecordTypeFilter(recordType = action.recordType)
            }

            is RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet -> {
                updateShowAddNewRecordBottomSheet(showAddNewRecordBottomSheet = action.showAddNewRecordBottomSheet)
            }

            // these are handled in UI layer and flow should ideally never come here
            is RecordScreenAction.OnAddNewRecord, is RecordScreenAction.OnRecordClick -> {
                Utils.crashInDebugBuild(errorMessage = "${action.javaClass.simpleName} should have been handled in UI layer")
            }

            is RecordScreenAction.OnCancelClickFromRationaleDialog -> {
                analyticsHelper.logEvent(
                    AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK
                ) { param(AnalyticsParam.DO_NOT_ASK_AGAIN, action.neverAsk) }

                if (action.neverAsk) {
                    updateNotificationPermissionState(
                        isNotificationPermissionAskedBefore = notificationPermissionState.value.isNotificationPermissionAskedBefore,
                        isNeverAskForNotificationPermission = true
                    )
                }
            }

            is RecordScreenAction.OnNotificationAllowedFromRationaleDialog -> {
                val isNotificationPermissionAskedBefore =
                    notificationPermissionState.value.isNotificationPermissionAskedBefore

                analyticsHelper.logEvent(
                    AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK,
                ) {
                    param(
                        AnalyticsParam.PERMISSION_ASKED_BEFORE,
                        isNotificationPermissionAskedBefore
                    )
                    param(
                        AnalyticsParam.REDIRECT_TO_SETTINGS,
                        action.isRedirectingToSettingsPage
                    )
                }

                if (isNotificationPermissionAskedBefore.not()) {
                    // allow clicked for first time
                    updateNotificationPermissionState(
                        isNotificationPermissionAskedBefore = true,
                        isNeverAskForNotificationPermission = notificationPermissionState.value.isNeverAskForNotificationPermission
                    )
                }
            }
        }
    }

    private fun updateNotificationPermissionState(
        isNotificationPermissionAskedBefore: Boolean,
        isNeverAskForNotificationPermission: Boolean
    ) {
        _notificationPermissionState.update {
            it.copy(
                isNotificationPermissionAskedBefore = isNotificationPermissionAskedBefore,
                isNeverAskForNotificationPermission = isNeverAskForNotificationPermission
            )
        }

        viewModelScope.launch {
            if (isNotificationPermissionAskedBefore) {
                preferenceProvider.upsertBooleanPref(
                    CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    true
                )
            }

            if (isNeverAskForNotificationPermission) {
                preferenceProvider.upsertBooleanPref(
                    CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    true
                )
            }
        }
    }

    private fun updateShowAddNewRecordBottomSheet(showAddNewRecordBottomSheet: Boolean) {
        userInputs.update {
            it.copy(
                isAddNewRecordBottomSheetVisible = showAddNewRecordBottomSheet
            )
        }
    }

    private fun onSearchTextUpdate(searchText: String) {
        userInputs.update {
            it.copy(
                searchText = searchText
            )
        }
    }

    private fun onToggleRecordTypeFilter(recordType: RecordType) {
        userInputs.update {
            val newFilterState: List<UserInputs.RecordTypeFilter> =
                it.recordTypeFilters.map { filter ->
                    val newFilter = if (filter.recordType == recordType) {
                        filter.copy(isSelected = !filter.isSelected)
                    } else {
                        filter
                    }
                    newFilter
                }

            it.copy(recordTypeFilters = newFilterState)
        }
    }

    companion object {
        // ask for review after every 5th login
        const val ASK_FOR_REVIEW_AFTER_EVERY_LOGIN = 5
    }
}
