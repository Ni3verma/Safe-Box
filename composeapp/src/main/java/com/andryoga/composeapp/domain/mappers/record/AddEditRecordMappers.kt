package com.andryoga.composeapp.domain.mappers.record

import com.andryoga.composeapp.data.db.entity.BankAccountDataEntity
import com.andryoga.composeapp.data.db.entity.BankCardDataEntity
import com.andryoga.composeapp.data.db.entity.LoginDataEntity
import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import com.andryoga.composeapp.domain.models.record.BankAccountData
import com.andryoga.composeapp.domain.models.record.CardData
import com.andryoga.composeapp.domain.models.record.LoginData
import com.andryoga.composeapp.domain.models.record.NoteData
import java.util.Date

fun NoteData.toDbEntity(): SecureNoteDataEntity {
    return SecureNoteDataEntity(
        key = id ?: 0,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}

fun SecureNoteDataEntity.toNoteData(): NoteData {
    return NoteData(
        id = key,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}

fun LoginData.toDbEntity(): LoginDataEntity {
    return LoginDataEntity(
        key = id ?: 0,
        title = title,
        url = url,
        userId = userId,
        password = password,
        notes = notes,
        creationDate = creationDate,
        updateDate = Date()
    )
}

fun LoginDataEntity.toLoginData(): LoginData {
    return LoginData(
        id = key,
        title = title,
        url = url,
        userId = userId,
        password = password,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}

fun BankAccountData.toDbEntity(): BankAccountDataEntity {
    return BankAccountDataEntity(
        key = id ?: 0,
        title = title,
        accountNumber = accountNo,
        customerName = customerName,
        customerId = customerId,
        branchCode = branchCode,
        branchName = branchName,
        branchAddress = branchAddress,
        ifscCode = ifscCode,
        micrCode = micrCode,
        notes = notes,
        creationDate = creationDate,
        updateDate = Date()
    )
}

fun BankAccountDataEntity.toBankAccountData(): BankAccountData {
    return BankAccountData(
        id = key,
        title = title,
        accountNo = accountNumber,
        customerName = customerName,
        customerId = customerId,
        branchCode = branchCode,
        branchName = branchName,
        branchAddress = branchAddress,
        ifscCode = ifscCode,
        micrCode = micrCode,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}

fun CardData.toDbEntity(): BankCardDataEntity {
    return BankCardDataEntity(
        key = id ?: 0,
        title = title,
        name = name,
        number = number,
        pin = pin,
        cvv = cvv,
        expiryDate = expiryDate,
        notes = notes,
        creationDate = creationDate,
        updateDate = Date()
    )
}

fun BankCardDataEntity.toCardData(): CardData {
    return CardData(
        id = key,
        title = title,
        name = name,
        number = number,
        pin = pin,
        cvv = cvv,
        expiryDate = expiryDate,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}