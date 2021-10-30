package com.andryoga.safebox.common

import com.andryoga.safebox.common.Utils.getFormattedDate
import com.andryoga.safebox.data.db.docs.ViewBankAccountData
import com.andryoga.safebox.data.db.docs.ViewBankCardData
import com.andryoga.safebox.data.db.docs.ViewLoginData
import com.andryoga.safebox.data.db.docs.ViewSecureNoteData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity

object DomainMappers {
    fun BankAccountDataEntity.toViewBankAccountData(): ViewBankAccountData {
        return ViewBankAccountData(
            key, title, accountNumber, customerName, customerId,
            branchCode, branchName, branchAddress, ifscCode, micrCode, notes,
            getFormattedDate(creationDate),
            getFormattedDate(updateDate)
        )
    }

    fun BankCardDataEntity.toViewBankCardData(): ViewBankCardData {
        return ViewBankCardData(
            key, title, name, number, pin, cvv, expiryDate, notes,
            getFormattedDate(creationDate),
            getFormattedDate(updateDate)
        )
    }

    fun LoginDataEntity.toViewLoginData(): ViewLoginData {
        return ViewLoginData(
            key, title, url, password, userId, notes,
            getFormattedDate(creationDate),
            getFormattedDate(updateDate)
        )
    }

    fun SecureNoteDataEntity.toViewSecureNoteData(): ViewSecureNoteData {
        return ViewSecureNoteData(
            key, title, notes,
            getFormattedDate(creationDate),
            getFormattedDate(updateDate)
        )
    }
}
