package com.andryoga.safebox.common

import com.andryoga.safebox.data.db.docs.ViewBankAccountData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import java.text.SimpleDateFormat

object DomainMappers {
    fun BankAccountDataEntity.toViewBankAccountData(): ViewBankAccountData {
        return ViewBankAccountData(
            key,
            title,
            accountNumber,
            customerName,
            customerId,
            branchCode,
            branchName,
            branchAddress,
            ifscCode,
            micrCode,
            notes,
            SimpleDateFormat("EEEE, dd MMM yyyy hh-mm-ss a").format(creationDate),
            SimpleDateFormat("EEEE, dd MMM yyyy hh-mm-ss a").format(updateDate)
        )
    }
}
