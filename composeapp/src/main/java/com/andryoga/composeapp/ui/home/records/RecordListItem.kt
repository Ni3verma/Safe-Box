package com.andryoga.composeapp.ui.home.records

data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val type: RecordListItem.Type
) {
    enum class Type {
        LOGIN, CARD, BANK_ACCOUNT, NOTE
    }
}
