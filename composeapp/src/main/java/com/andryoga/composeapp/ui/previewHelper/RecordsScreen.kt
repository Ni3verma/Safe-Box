package com.andryoga.composeapp.ui.previewHelper

import com.andryoga.composeapp.ui.core.models.RecordType
import com.andryoga.composeapp.ui.home.records.RecordListItem
import kotlin.random.Random

fun getRecordList(): List<RecordListItem> {
    val records = mutableListOf<RecordListItem>()
    repeat(30) {
        val randomTypeIndex = Random.nextInt(0, 4)
        val isSubtitlePresent = Random.nextBoolean()
        records.add(
            RecordListItem(
                id = it,
                title = "$it - title",
                subTitle = if (isSubtitlePresent) "$it - subtitle" else null,
                recordType = RecordType.entries[randomTypeIndex]
            )
        )
    }

    return records
}

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