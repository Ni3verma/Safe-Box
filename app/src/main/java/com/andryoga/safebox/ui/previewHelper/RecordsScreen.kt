package com.andryoga.safebox.ui.previewHelper

import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.home.records.models.RecordsState
import kotlin.random.Random

fun getRecordState(): RecordsState {
    val records = mutableListOf<RecordListItem>()
    // i is chosen in this range so that id doesn't conflict with existing records
    // if this is ever used for fake data while debugging issues
    for (i in 1000..1100) {
        val randomTypeIndex = Random.nextInt(0, 4)
        val isSubtitlePresent = Random.nextBoolean()
        records.add(
            RecordListItem(
                id = i,
                title = "$i - title",
                subTitle = if (isSubtitlePresent) "$i - subtitle" else null,
                recordType = RecordType.entries[randomTypeIndex]
            )
        )
    }

    return RecordsState(
        records = records,
        totalDbRecords = records.size
    )
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