package com.andryoga.composeapp.ui.singleRecord

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.Layout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SingleRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState: MutableStateFlow<SingleRecordScreenUiState> =
        MutableStateFlow(SingleRecordScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val layout: Layout


    init {
        val args = savedStateHandle.toRoute<SingleRecordScreenRoute>()
        layout = LayoutFactory.getLayout(args.recordType)

        _uiState.update {
            it.copy(
                isLoading = false,
                layoutPlan = layout.getLayoutPlan()
            )
        }
    }

    fun onAction(action: SingleRecordScreenAction) {
        when (action) {
            is SingleRecordScreenAction.OnCellValueUpdate -> {
                _uiState.update { currentState ->
                    val fieldToUpdate = currentState.layoutPlan.fieldUiState[action.fieldId]
                        ?: return@update currentState
                    val updatedField = fieldToUpdate.copy(data = action.data)
                    val updatedUiState =
                        currentState.layoutPlan.fieldUiState + (action.fieldId to updatedField)


                    currentState.copy(
                        layoutPlan = currentState.layoutPlan.copy(
                            fieldUiState = updatedUiState,
                        ),
                        isSaveEnabled = layout.checkMandatoryFields(updatedUiState.values)
                    )
                }
            }

            is SingleRecordScreenAction.OnSaveClicked -> {
                Timber.i("save clicked")
            }
        }
    }
}