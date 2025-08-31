package com.andryoga.composeapp.ui.home.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            val records = mutableListOf<RecordListItem>()
            repeat(30) {
                records.add(
                    RecordListItem(
                        it,
                        "$it - title", "$it - subtitle", RecordListItem.Type.LOGIN
                    )
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    records = records
                )
            }
        }
    }
}