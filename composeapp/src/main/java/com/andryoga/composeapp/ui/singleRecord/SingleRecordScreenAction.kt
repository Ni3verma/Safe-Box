package com.andryoga.composeapp.ui.singleRecord

sealed interface SingleRecordScreenAction {
    data class onCellValueUdate(
        val data: String,
        val rowIndex: Int,
        val columnIndex: Int,
    ) : SingleRecordScreenAction
}