package com.andryoga.safebox.ui.singleRecord

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.R
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.toAnalyticsValue
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val activeSessionManager: Lazy<ActiveSessionManager>,
    singleRecordRouteProvider: SingleRecordRouteProvider,
    layoutFactory: LayoutFactory,
    @param:ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {
    private val _uiState: MutableStateFlow<SingleRecordScreenUiState> =
        MutableStateFlow(SingleRecordScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val _screenCloseEvent = MutableSharedFlow<Unit>()
    val screenCloseEvent = _screenCloseEvent.asSharedFlow()

    private val _shareContentEvent = MutableSharedFlow<String>()
    val shareContentEvent: SharedFlow<String> = _shareContentEvent.asSharedFlow()
    private val layout: Layout
    private val recordType: RecordType
    private val isNewRecord: Boolean

    /**
     * Field values the create form opened with, captured when the plan loads. Comparing against
     * them rather than against empty strings is what lets a QR-prefilled authenticator that was
     * never touched report `had_input = false`.
     */
    private var initialFieldData: Map<FieldId, String> = emptyMap()
    private var isSaved = false

    init {
        val args = singleRecordRouteProvider.getRoute()
        recordType = args.recordType
        isNewRecord = args.id == null
        layout = layoutFactory.getLayout(args.id, args.recordType)
        Timber.i("got layout of type : ${args.recordType}, is id null : ${args.id == null}")

        viewModelScope.launch {
            val layoutPlan = layout.getLayoutPlan()
            initialFieldData = layoutPlan.fieldUiState.mapValues { it.value.data }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    layoutPlan = layoutPlan,
                    viewMode = if (isNewRecord) ViewMode.NEW else ViewMode.VIEW,
                    topAppBarUiState = SingleRecordScreenUiState.TopAppBarUiState(
                        title = getTitleForTopAppBar(args.recordType),
                        isSaveButtonVisible = isNewRecord,
                        // a layout can open already complete, which is how a scanned QR code
                        // arrives: title and seed are prefilled and the user has nothing left to
                        // type. Seeding this from the plan keeps Save usable in that case, since
                        // OnCellValueUpdate is the only other place it is ever recomputed.
                        isSaveButtonEnabled = layout.checkMandatoryFields(layoutPlan.fieldUiState),
                    ),
                )
            }
        }
    }

    /**
     * A create form whose ViewModel is cleared before a save is an abandoned form, whichever way
     * it was left: back, the away timeout popping the home graph, or the activity finishing. The
     * ViewModel outlives configuration changes, so a rotation is not counted. Process death fires
     * nothing, which under-counts slightly rather than over-counting.
     */
    override fun onCleared() {
        if (isNewRecord && !isSaved) {
            val currentFieldData = _uiState.value.layoutPlan.fieldUiState.mapValues { it.value.data }
            analyticsHelper.logEvent(AnalyticsKey.RECORD_FORM_ABANDON) {
                param(AnalyticsParam.RECORD_TYPE, recordType.toAnalyticsValue())
                param(AnalyticsParam.HAD_INPUT, currentFieldData != initialFieldData)
            }
        }
        super.onCleared()
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
                            isSaveButtonEnabled = layout.checkMandatoryFields(updatedUiState)
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
                    isSaved = true
                    _screenCloseEvent.emit(Unit)
                }
            }

            SingleRecordScreenAction.OnDeleteClicked -> {
                Timber.i("delete clicked")
                viewModelScope.launch {
                    layout.deleteLayout()
                    // after the delete, like isSaved after the save: a failed delete is not one
                    analyticsHelper.logEvent(AnalyticsKey.RECORD_DELETE) {
                        param(AnalyticsParam.RECORD_TYPE, recordType.toAnalyticsValue())
                    }
                    _screenCloseEvent.emit(Unit)
                }
            }
            SingleRecordScreenAction.OnEditClicked -> {
                Timber.i("edit clicked")
                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.EDIT,
                        topAppBarUiState = it.topAppBarUiState.copy(
                            isSaveButtonVisible = true
                        )
                    )
                }
            }

            SingleRecordScreenAction.OnShareClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.RECORD_SHARE) {
                    param(AnalyticsParam.RECORD_TYPE, recordType.toAnalyticsValue())
                }
                handleShareRecord()
            }

            SingleRecordScreenAction.OnCopyTotpCode -> {
                analyticsHelper.logEvent(AnalyticsKey.AUTHENTICATOR_COPY_CLICK) {
                    param(AnalyticsParam.SOURCE, AnalyticsSource.RECORD_DETAIL.value)
                }
            }

            is SingleRecordScreenAction.OnFieldCopied -> {
                analyticsHelper.logEvent(AnalyticsKey.FIELD_COPY) {
                    param(AnalyticsParam.RECORD_TYPE, recordType.toAnalyticsValue())
                    // the field's name, e.g. login_password; the value never leaves the device
                    param(AnalyticsParam.FIELD, action.fieldId.name.lowercase())
                }
            }
        }
    }

    private fun handleShareRecord() {
        Timber.i("on share clicked")
        viewModelScope.launch(dispatchersProvider.io) {
            Timber.i("making copyable content")
            val dataStringBuffer = StringBuffer()

            layout.getShareableFields().forEach { shareableField ->
                val cellTitle = context.getString(shareableField.label)
                dataStringBuffer.append("$cellTitle : ${shareableField.value}\n")
            }

            dataStringBuffer.append(
                "---------------\n${
                    context.getString(
                        R.string.common_app_playstore_download,
                        CommonConstants.APP_PLAYSTORE_LINK_SHARE
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
                    isSaveButtonVisible = false,
                ),
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
                RecordType.AUTHENTICATOR -> R.string.type_display_authenticator
            }
        )
    }
}