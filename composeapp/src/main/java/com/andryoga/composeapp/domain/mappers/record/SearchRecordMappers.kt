package com.andryoga.composeapp.domain.mappers.record

import com.andryoga.composeapp.data.db.docs.SearchBankAccountData
import com.andryoga.composeapp.data.db.docs.SearchBankCardData
import com.andryoga.composeapp.data.db.docs.SearchLoginData
import com.andryoga.composeapp.data.db.docs.SearchSecureNoteData
import com.andryoga.composeapp.domain.models.record.RecordListItem
import com.andryoga.composeapp.domain.models.record.RecordType

fun SearchSecureNoteData.toRecordListItem(): RecordListItem {
    return RecordListItem(
        id = key,
        title = title,
        subTitle = null,
        recordType = RecordType.NOTE
    )
}

fun SearchBankAccountData.toRecordListItem(): RecordListItem {
    return RecordListItem(
        id = key,
        title = title,
        subTitle = accountNumber,
        recordType = RecordType.BANK_ACCOUNT
    )
}

fun SearchBankCardData.toRecordListItem(): RecordListItem {
    return RecordListItem(
        id = key,
        title = title,
        subTitle = number,
        recordType = RecordType.CARD
    )
}

fun SearchLoginData.toRecordListItem(): RecordListItem {
    return RecordListItem(
        id = key,
        title = title,
        subTitle = userId,
        recordType = RecordType.LOGIN
    )

}