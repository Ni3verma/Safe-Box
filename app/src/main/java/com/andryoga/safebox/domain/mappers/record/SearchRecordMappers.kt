package com.andryoga.safebox.domain.mappers.record

import com.andryoga.safebox.data.db.docs.SearchAuthenticatorData
import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType

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

/**
 * Maps an authenticator record to a list row.
 *
 * [SearchAuthenticatorData.secretKey] is already decrypted by the secure DAO, so the seed is passed
 * straight through for the card to derive the rolling code. [RecordListItem.subTitle] stays null
 * because the one-time code occupies that slot instead of a static value.
 */
fun SearchAuthenticatorData.toRecordListItem(): RecordListItem {
    return RecordListItem(
        id = key,
        title = title,
        subTitle = null,
        recordType = RecordType.AUTHENTICATOR,
        totpSecret = secretKey,
    )
}