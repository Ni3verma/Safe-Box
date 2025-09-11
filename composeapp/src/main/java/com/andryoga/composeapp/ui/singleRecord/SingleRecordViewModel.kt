package com.andryoga.composeapp.ui.singleRecord

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.andryoga.composeapp.ui.core.models.RecordType
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SingleRecordViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState: MutableStateFlow<SingleRecordScreenUiState> =
        MutableStateFlow(SingleRecordScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val args = savedStateHandle.toRoute<SingleRecordScreenRoute>()
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
            is SingleRecordScreenAction.OnCellValueUpdate -> {
                _uiState.update { currentState ->
                    val fieldToUpdate = currentState.layout.fieldUiState[action.fieldId]
                        ?: return@update currentState
                    val updatedField = fieldToUpdate.copy(data = action.data)
                    val updatedUiState =
                        currentState.layout.fieldUiState + (action.fieldId to updatedField)


                    currentState.copy(
                        layout = currentState.layout.copy(
                            fieldUiState = updatedUiState
                        )
                    )
                }
            }
        }
    }
}