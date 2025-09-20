package com.andryoga.composeapp.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.domain.mappers.record.toRecordListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

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

            val filteredListItemFlow = combine(
                combinedListItemFlow,
                searchText
            ) { combinedList, searchText ->
                if (searchText.isEmpty()) {
                    combinedList
                } else {
                    combinedList.filter { it.title.contains(searchText, ignoreCase = true) }
                }
            }

            filteredListItemFlow.collect { newCombinedList ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        records = newCombinedList
                    )
                }
            }
        }
    }

    fun updateShowAddNewRecordBottomSheet(showAddNewRecordBottomSheet: Boolean) {
        _uiState.update {
            it.copy(
                isShowAddNewRecordsBottomSheet = showAddNewRecordBottomSheet
            )
        }
    }

    fun onSearchTextUpdate(searchText: String) {
        _searchText.value = searchText
    }

    fun onClearSearchText() {
        _searchText.value = ""
    }
}
