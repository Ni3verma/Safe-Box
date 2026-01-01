package com.andryoga.composeapp.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.common.CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION
import com.andryoga.composeapp.common.CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE
import com.andryoga.composeapp.common.Utils.crashInDebugBuild
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.domain.mappers.record.toRecordListItem
import com.andryoga.composeapp.domain.models.record.RecordListItem
import com.andryoga.composeapp.domain.models.record.RecordType
import com.andryoga.composeapp.providers.interfaces.PreferenceProvider
import com.andryoga.composeapp.ui.home.records.models.NotificationPermissionState
import com.andryoga.composeapp.ui.home.records.models.RecordsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    private val preferenceProvider: PreferenceProvider
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


    init {
        loadRecords()

        viewModelScope.launch {
            _notificationPermissionState.value = NotificationPermissionState(
                isNotificationPermissionAskedBefore = preferenceProvider.getBooleanPref(
                    IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    false
                ),
                isNeverAskForNotificationPermission = preferenceProvider.getBooleanPref(
                    IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    false
                ),
                isBackupPathSet = backupMetadataRepository.isBackupPathSet()
            )
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
                crashInDebugBuild(errorMessage = "${action.javaClass.simpleName} should have been handled in UI layer")
            }

            RecordScreenAction.OnNeverAskForNotificationPermission -> updateNotificationPermissionState(
                isNotificationPermissionAskedBefore = notificationPermissionState.value.isNotificationPermissionAskedBefore,
                isNeverAskForNotificationPermission = true
            )

            RecordScreenAction.OnNotificationPermissionAskedForFirstTime -> updateNotificationPermissionState(
                isNotificationPermissionAskedBefore = true,
                isNeverAskForNotificationPermission = notificationPermissionState.value.isNeverAskForNotificationPermission
            )
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
                preferenceProvider.upsertBooleanPref(IS_NOTIFICATION_PERMISSION_ASKED_BEFORE, true)
            }

            if (isNeverAskForNotificationPermission) {
                preferenceProvider.upsertBooleanPref(IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION, true)
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
}
