package com.andryoga.safebox.ui.home.records

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.VaultStorageSnapshot
import com.andryoga.safebox.common.VaultStorageStats
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
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
import com.andryoga.safebox.ui.utils.toAnalyticsValue
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    bankAccountDataRepository: BankAccountDataRepository,
    secureNoteDataRepository: SecureNoteDataRepository,
    loginDataRepository: LoginDataRepository,
    cardDataRepository: BankCardDataRepository,
    authenticatorDataRepository: AuthenticatorDataRepository,
    private val dispatchersProvider: DispatchersProvider,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val preferenceProvider: PreferenceProvider,
    private val analyticsHelper: AnalyticsHelper,
    private val vaultStorageStats: VaultStorageStats,
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
            Timber.i("eligible for in app review")
            emit(Unit)
        }
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        replay = 0
    )

    private val dbRecords = combine(
        bankAccountDataRepository.getAllBankAccountData(),
        secureNoteDataRepository.getAllSecureNoteData(),
        loginDataRepository.getAllLoginData(),
        cardDataRepository.getAllBankCardData(),
        authenticatorDataRepository.getAllAuthenticatorData(),
    ) { bankAccountData, secureNoteData, loginData, cardData, authenticatorData ->
        val combinedList = bankAccountData.map { it.toRecordListItem() } +
                secureNoteData.map { it.toRecordListItem() } +
                loginData.map { it.toRecordListItem() } +
                cardData.map { it.toRecordListItem() } +
                authenticatorData.map { it.toRecordListItem() }
        combinedList.sortedBy { it.title.lowercase() }
    }
        // sits inside the shared pipeline so it runs only once a collector exists
        .onEach { logVaultStatsOnce(recordCount = it.size) }
        .flowOn(dispatchersProvider.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val userInputs = MutableStateFlow(UserInputs())

    /** The empty layout re-enters composition on every return to the list; log its show once. */
    private var isEmptyVaultShowLogged = false

    /** The pipeline above re-emits on every record change; the vault size is logged once per ViewModel. */
    private val isVaultStatsLogged = AtomicBoolean(false)

    /**
     * Driving state of the search bar, owned here so it survives navigation to a record and back.
     *
     * The text field edits this directly and reads it back in the same frame. Routing the query
     * through [userInputs] instead — `onValueChange` → flow → `combine` on the default dispatcher →
     * `collectAsState` — fed the field a stale value whenever two key presses landed within one
     * round trip, which reset the caret one position short of the end. The derived
     * [RecordsUiState.searchText] still exists for consumers that only need the applied query.
     */
    val searchTextState = TextFieldState()

    private val searchText: Flow<String> = snapshotFlow { searchTextState.text.toString() }

    init {
        // the first non-blank query ends this collector, so one event per ViewModel
        searchText
            .filter { it.isNotBlank() }
            .take(1)
            .onEach { analyticsHelper.logEvent(AnalyticsKey.SEARCH_USED) }
            .launchIn(viewModelScope)
    }

    val uiState = combine(dbRecords, userInputs, searchText) { dbRecords, userInputs, searchText ->
        if (dbRecords == null) {
            RecordsUiState()
        } else {
            var filteredRecords = dbRecords
            val totalRecordsInDb = dbRecords.size
            filteredRecords = dbRecords.filter {
                it.title.contains(
                    searchText,
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
                searchText = searchText,
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
            RecordScreenAction.OnClearSearchText -> {
                searchTextState.clearText()
            }

            is RecordScreenAction.OnToggleRecordTypeFilter -> {
                onToggleRecordTypeFilter(recordType = action.recordType)
            }

            is RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet -> {
                if (action.showAddNewRecordBottomSheet) {
                    analyticsHelper.logEvent(AnalyticsKey.ADD_RECORD_SHEET_SHOW)
                }
                updateShowAddNewRecordBottomSheet(showAddNewRecordBottomSheet = action.showAddNewRecordBottomSheet)
            }

            // navigation happens in the UI layer; only the event is recorded here
            is RecordScreenAction.OnAddNewRecord -> {
                analyticsHelper.logEvent(AnalyticsKey.ADD_RECORD_TYPE_PICK) {
                    param(AnalyticsParam.RECORD_TYPE, action.recordType.toAnalyticsValue())
                    param(AnalyticsParam.SOURCE, action.source.value)
                }
            }

            RecordScreenAction.OnEmptyVaultShown -> {
                if (!isEmptyVaultShowLogged) {
                    isEmptyVaultShowLogged = true
                    analyticsHelper.logEvent(AnalyticsKey.EMPTY_VAULT_SHOW)
                }
            }

            RecordScreenAction.OnCopyTotpCode -> {
                analyticsHelper.logEvent(AnalyticsKey.AUTHENTICATOR_COPY_CLICK) {
                    param(AnalyticsParam.SOURCE, AnalyticsSource.RECORDS_LIST.value)
                }
            }

            is RecordScreenAction.OnRecordClick -> {
                analyticsHelper.logEvent(AnalyticsKey.RECORD_OPEN) {
                    param(AnalyticsParam.RECORD_TYPE, action.recordType.toAnalyticsValue())
                }
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

    /**
     * Logs [AnalyticsKey.VAULT_STATS] for the first record list this ViewModel sees.
     *
     * @param recordCount records across all five types, from the list that just arrived.
     */
    private fun logVaultStatsOnce(recordCount: Int) {
        if (!isVaultStatsLogged.compareAndSet(false, true)) return
        viewModelScope.launch(dispatchersProvider.io) {
            // telemetry only: a failed storage read reports -1 for both sizes, like the reader itself
            val storage = runCatching { vaultStorageStats.snapshot() }
                .getOrDefault(VaultStorageSnapshot.UNAVAILABLE)
            analyticsHelper.logEvent(AnalyticsKey.VAULT_STATS) {
                param(AnalyticsParam.RECORD_COUNT, recordCount)
                param(AnalyticsParam.DB_SIZE_KB, storage.dbSizeKb)
                param(AnalyticsParam.FREE_SPACE_MB, storage.freeSpaceMb)
            }
        }
    }

    companion object {
        // ask for review after every 5th login
        const val ASK_FOR_REVIEW_AFTER_EVERY_LOGIN = 5
    }
}
