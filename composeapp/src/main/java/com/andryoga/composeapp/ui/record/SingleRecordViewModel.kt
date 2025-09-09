package com.andryoga.composeapp.ui.record

import androidx.lifecycle.ViewModel
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.ui.core.models.RecordType
import com.andryoga.composeapp.ui.record.dynamicLayout.Layout
import com.andryoga.composeapp.ui.record.dynamicLayout.LayoutFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SingleRecordViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {
    private val _uiState: MutableStateFlow<SingleRecordScreenUiState> =
        MutableStateFlow(SingleRecordScreenUiState())
    val uiState = _uiState.asStateFlow()

    fun initVM(args: SingleRecordScreenRoute) {
        if (!_uiState.value.isLoading) return

        val layout = when (args.recordType) {
            RecordType.LOGIN -> LayoutFactory.getLoginRecordLayout()
            RecordType.CARD -> LayoutFactory.getCardRecordLayout()
            RecordType.BANK_ACCOUNT -> LayoutFactory.getBankAccountRecordLayout()
            RecordType.NOTE -> LayoutFactory.getNoteRecordLayout()
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                layout = layout
            )
        }
    }

    fun onAction(action: SingleRecordScreenAction) {
        when (action) {
            is SingleRecordScreenAction.onCellValueUdate -> {
                _uiState.update { currentState ->
                    val rowToUpdate =
                        currentState.layout.rows[action.rowIndex] ?: return@update currentState

                    val updatedUiState =
                        rowToUpdate[action.columnIndex].uiState.copy(data = action.data)
                    val updatedRow = rowToUpdate[action.columnIndex].copy(
                        uiState = updatedUiState
                    )

                    val newRow: List<Layout.Field> = rowToUpdate.toMutableList().apply {
                        set(action.columnIndex, updatedRow)
                    }

                    val newRows = currentState.layout.rows + (action.rowIndex to newRow)

                    currentState.copy(
                        layout = currentState.layout.copy(
                            rows = newRows
                        )
                    )
                }

            }
        }
    }
}