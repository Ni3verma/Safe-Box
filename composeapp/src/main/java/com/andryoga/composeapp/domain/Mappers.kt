package com.andryoga.composeapp.domain

import com.andryoga.composeapp.data.db.entity.BankAccountDataEntity
import com.andryoga.composeapp.data.db.entity.BankCardDataEntity
import com.andryoga.composeapp.data.db.entity.LoginDataEntity
import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import com.andryoga.composeapp.ui.core.models.BankAccountData
import com.andryoga.composeapp.ui.core.models.CardData
import com.andryoga.composeapp.ui.core.models.LoginData
import com.andryoga.composeapp.ui.core.models.NoteData
import java.util.Date

fun NoteData.toDbEntity(): SecureNoteDataEntity {
    return SecureNoteDataEntity(
        key = id ?: 0,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = Date()
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