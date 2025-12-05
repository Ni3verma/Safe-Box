package com.andryoga.composeapp.ui.singleRecord

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.andryoga.composeapp.R
import com.andryoga.composeapp.common.CommonConstants.APP_PLAYSTORE_LINK
import com.andryoga.composeapp.domain.models.record.RecordType
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SingleRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    layoutFactory: LayoutFactory
) : ViewModel() {
    private val _uiState: MutableStateFlow<SingleRecordScreenUiState> =
        MutableStateFlow(SingleRecordScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val _screenCloseEvent = MutableSharedFlow<Unit>()
    val screenCloseEvent = _screenCloseEvent.asSharedFlow()

    private val _shareContentEvent = MutableSharedFlow<String>()
    val shareContentEvent: SharedFlow<String> = _shareContentEvent.asSharedFlow()
    private val layout: Layout

    init {
        val args = savedStateHandle.toRoute<SingleRecordScreenRoute>()
        layout = layoutFactory.getLayout(args.id, args.recordType)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    layoutPlan = layout.getLayoutPlan(),
                    viewMode = if (args.id != null) ViewMode.VIEW else ViewMode.NEW,
                    topAppBarUiState = if (args.id != null) SingleRecordScreenUiState.TopAppBarUiState(
                        isSaveButtonVisible = false,
                        title = getTitleForTopAppBar(args.recordType)
                    ) else SingleRecordScreenUiState.TopAppBarUiState(
                        isSaveButtonVisible = true,
                        title = getTitleForTopAppBar(args.recordType)
                    )
                )
            }
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
                        topAppBarUiState = currentState.topAppBarUiState.copy(
                            isSaveButtonEnabled = layout.checkMandatoryFields(updatedUiState.values)
                        )
                    )
                }
            }

            is SingleRecordScreenAction.OnSaveClicked -> {
                Timber.i("save clicked")
                viewModelScope.launch {
                    layout.saveLayout(
                        _uiState.value.layoutPlan.fieldUiState.mapValues { it.value.data }
                    )
                    _screenCloseEvent.emit(Unit)
                }
            }

            SingleRecordScreenAction.OnDeleteClicked -> {
                viewModelScope.launch {
                    layout.deleteLayout()
                    _screenCloseEvent.emit(Unit)
                }
            }
            SingleRecordScreenAction.OnEditClicked -> {
                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.EDIT,
                        topAppBarUiState = it.topAppBarUiState.copy(
                            isSaveButtonVisible = true
                        )
                    )
                }
            }

            SingleRecordScreenAction.OnShareClicked -> handleShareRecord()
        }
    }

    fun onBackClick() {
        if (uiState.value.viewMode == ViewMode.EDIT) {
            goBackToViewMode()
        } else {
            viewModelScope.launch {
                _screenCloseEvent.emit(Unit)
            }
        }
    }

    private fun handleShareRecord() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.i("making copyable content")
            val dataStringBuffer = StringBuffer()

            layout.getLayoutPlan().fieldUiState.filter { (_, uiState) ->
                uiState.data.isEmpty().not() &&
                        uiState.cell.isCopyable &&
                        uiState.cell.isPasswordField.not()
            }.forEach { _, uiState ->
                val cellTitle = context.getString(uiState.cell.label)
                dataStringBuffer.append("$cellTitle : ${uiState.data}\n")
            }

            dataStringBuffer.append(
                "---------------\n${
                    context.getString(
                        R.string.common_app_playstore_download,
                        APP_PLAYSTORE_LINK
                    )
                }"
            )

            _shareContentEvent.emit(dataStringBuffer.toString())
        }

    }

    private fun goBackToViewMode() {
        _uiState.update {
            it.copy(
                viewMode = ViewMode.VIEW,
                topAppBarUiState = it.topAppBarUiState.copy(
                    isSaveButtonVisible = false
                )
            )
        }
    }

    private fun getTitleForTopAppBar(recordType: RecordType): String {
        return context.getString(
            when (recordType) {
                RecordType.LOGIN -> R.string.type_display_login
                RecordType.CARD -> R.string.type_display_card
                RecordType.BANK_ACCOUNT -> R.string.type_display_account
                RecordType.NOTE -> R.string.type_display_note
            }
        )
    }
}