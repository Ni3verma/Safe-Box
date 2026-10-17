package com.andryoga.safebox.ui.home.records

import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.domain.models.record.RecordType

sealed interface RecordScreenAction {
    /**
     * User tapped the clear icon in the search bar. Typing itself is not an action: the search
     * field edits `RecordsViewModel.searchTextState` directly.
     */
    data object OnClearSearchText : RecordScreenAction
    data class OnUpdateShowAddNewRecordBottomSheet(val showAddNewRecordBottomSheet: Boolean) :
        RecordScreenAction

    /**
     * User picked a record type to create. Navigation is the UI layer's job; the ViewModel only
     * records the pick.
     *
     * @param source which surface offered the type: the bottom sheet or the empty vault's cards.
     */
    data class OnAddNewRecord(
        val recordType: RecordType,
        val source: AnalyticsSource,
    ) : RecordScreenAction
    data class OnRecordClick(val id: Int, val recordType: RecordType) : RecordScreenAction
    data class OnToggleRecordTypeFilter(val recordType: RecordType) : RecordScreenAction

    /**
     * The empty-vault layout entered composition. Sent on every entry; the ViewModel decides how
     * often that is worth an analytics event.
     */
    data object OnEmptyVaultShown : RecordScreenAction

    /** User copied the live one-time code from an authenticator row. */
    object OnCopyTotpCode : RecordScreenAction

    /**
     * User clicked allow on notification permission rationale dialog.
     * He may or may not accept it from system dialog now.
     * */
    data class OnNotificationAllowedFromRationaleDialog(
        val isRedirectingToSettingsPage: Boolean
    ) : RecordScreenAction

    /**
     * User clicked cancel on notification permission rationale dialog
     * @param neverAsk: true if "never ask again" checkbox was checked.
     * */
    data class OnCancelClickFromRationaleDialog(val neverAsk: Boolean) : RecordScreenAction
}