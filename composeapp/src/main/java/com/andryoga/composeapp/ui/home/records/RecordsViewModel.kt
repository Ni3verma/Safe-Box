package com.andryoga.composeapp.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.common.Utils.crashInDebugBuild
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.domain.mappers.record.toRecordListItem
import com.andryoga.composeapp.domain.models.record.RecordListItem
import com.andryoga.composeapp.domain.models.record.RecordType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository,
    private val secureNoteDataRepository: SecureNoteDataRepository,
    private val loginDataRepository: LoginDataRepository,
    private val cardDataRepository: BankCardDataRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState = _uiState.asStateFlow()

    private val _records = MutableStateFlow(emptyList<RecordListItem>())
    val records = _records.asStateFlow()

    init {
        loadRecords()
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

            val filteredListItemFlow: Flow<List<RecordListItem>> = combine(
                combinedListItemFlow,
                _uiState
            ) { combinedList, currUiState ->
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
                _records.value = filteredList
            }
        }
    }

    fun onScreenAction(action: RecordScreenAction) {
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
