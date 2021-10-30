package com.andryoga.safebox.common

import com.andryoga.safebox.common.Utils.getFormattedDate
import com.andryoga.safebox.data.db.docs.ViewBankAccountData
import com.andryoga.safebox.data.db.docs.ViewBankCardData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity

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
}
