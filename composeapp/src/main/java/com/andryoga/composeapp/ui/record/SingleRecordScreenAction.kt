package com.andryoga.composeapp.ui.record

sealed interface SingleRecordScreenAction {
    data class onCellValueUdate(
        val data: String,
        val rowIndex: Int,
        val columnIndex: Int,
    ) : SingleRecordScreenAction
}