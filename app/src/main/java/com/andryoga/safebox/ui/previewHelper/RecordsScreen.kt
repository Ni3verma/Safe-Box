package com.andryoga.safebox.ui.previewHelper

import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.home.records.models.UserInputs

fun getLoginRecordItem() = RecordListItem(
    id = 1,
    title = "Instagram",
    subTitle = "Ni3_ve",
    recordType = RecordType.LOGIN
)

fun getBankAccountRecordItem() = RecordListItem(
    id = 1,
    title = "HDFC Bank",
    subTitle = "36128937981273897",
    recordType = RecordType.BANK_ACCOUNT
)

fun getCardRecordItem() = RecordListItem(
    id = 1,
    title = "Regalia Gold",
    subTitle = "3528387328758923",
    recordType = RecordType.CARD
)

fun getNoteRecordItem() = RecordListItem(
    id = 1,
    title = "Android learnings",
    subTitle = null,
    recordType = RecordType.NOTE
)

fun getAppliedRecordTypeFilters(): List<UserInputs.RecordTypeFilter> {
    return listOf(
        UserInputs.RecordTypeFilter(RecordType.LOGIN, true),
        UserInputs.RecordTypeFilter(RecordType.CARD, false),
        UserInputs.RecordTypeFilter(RecordType.BANK_ACCOUNT, false),
        UserInputs.RecordTypeFilter(RecordType.NOTE, false)
    )
}