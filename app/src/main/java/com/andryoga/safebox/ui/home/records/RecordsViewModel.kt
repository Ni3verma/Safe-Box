package com.andryoga.safebox.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.AnalyticsKeys
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.mappers.record.toRecordListItem
import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.andryoga.safebox.ui.home.records.models.NotificationPermissionState
import com.andryoga.safebox.ui.home.records.models.RecordsState
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository,
    private val secureNoteDataRepository: SecureNoteDataRepository,
    private val loginDataRepository: LoginDataRepository,
    private val cardDataRepository: BankCardDataRepository,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val preferenceProvider: PreferenceProvider,
    val inAppReviewManager: Lazy<InAppReviewManager>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState = _uiState.asStateFlow()

    private val _recordState = MutableStateFlow(RecordsState())
    val recordState = _recordState.asStateFlow()
    private val _notificationPermissionState = MutableStateFlow(
        NotificationPermissionState()
    )
    val notificationPermissionState: StateFlow<NotificationPermissionState> =
        _notificationPermissionState

    private val _startInAppReview = Channel<Unit>(Channel.CONFLATED)

    /**
     * Start In-App review flow if user logs in after x times again.
     * */
    val startInAppReview = _startInAppReview.receiveAsFlow()

    init {
        loadRecords()

        viewModelScope.launch {
            _notificationPermissionState.value = NotificationPermissionState(
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

            val totalLoginCount =
                preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
            if (totalLoginCount % ASK_FOR_REVIEW_AFTER_EVERY_LOGIN == 0) {
                _startInAppReview.send(Unit)
            }
        }

    }

    private fun loadRecords() {
        viewModelScope.launch {
            val combinedListItemFlow = combine(
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
            }.flowOn(Dispatchers.Default)

            var totalRecordsInDb = 0
            val filteredListItemFlow: Flow<List<RecordListItem>> = combine(
                combinedListItemFlow,
                _uiState
            ) { combinedList, currUiState ->
                totalRecordsInDb = combinedList.size
                val textFilteredList = combinedList.filter {
                    it.title.contains(
                        currUiState.searchText,
                        ignoreCase = true
                    )
                }

                val selectedFilters = currUiState.recordTypeFilters.filter { it.isSelected }
                if (selectedFilters.isNotEmpty()) {
                    val recordTypeToFilterOn = selectedFilters.map { it.recordType }
                    textFilteredList.filter { it.recordType in recordTypeToFilterOn }
                } else {
                    textFilteredList
                }
            }

            filteredListItemFlow.collect { filteredList ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                    )
                }
                _recordState.update {
                    it.copy(
                        records = filteredList,
                        totalDbRecords = totalRecordsInDb
                    )
                }
            }
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
                Firebase.analytics.logEvent(
                    AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK
                ) {
                    param(AnalyticsKeys.DO_NOT_ASK_AGAIN, action.neverAsk.toString())
                }

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
                Firebase.analytics.logEvent(AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK) {
                    param(
                        AnalyticsKeys.PERMISSION_ASKED_BEFORE,
                        isNotificationPermissionAskedBefore.toString()
                    )
                    param(
                        AnalyticsKeys.REDIRECT_TO_SETTINGS,
                        action.isRedirectingToSettingsPage.toString()
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
        _uiState.update {
            it.copy(
                isShowAddNewRecordsBottomSheet = showAddNewRecordBottomSheet
            )
        }
    }

    private fun onSearchTextUpdate(searchText: String) {
        _uiState.update {
            it.copy(
                searchText = searchText
            )
        }
    }

    private fun onToggleRecordTypeFilter(recordType: RecordType) {
        _uiState.update {
            val newFilterState: List<RecordsUiState.RecordTypeFilter> =
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
        // ask for review after every 10th login
        private const val ASK_FOR_REVIEW_AFTER_EVERY_LOGIN = 10
    }
}
