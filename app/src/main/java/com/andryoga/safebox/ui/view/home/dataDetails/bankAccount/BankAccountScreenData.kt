package com.andryoga.safebox.ui.view.home.dataDetails.bankAccount

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class BankAccountScreenData(
    /*
     * It is very important to initialize key with 0
     * so that when we convert screen data to entity for db insertion at that
     * 0 will be passed. For room zero means that it can auto-increment value
     * */
    pKey: Int = 0,
    pTitle: String = "",
    pAccountNo: String = "",
    pCustomerName: String? = null,
    pCustomerId: String? = null,
    pBranchCode: String? = null,
    pBranchName: String? = null,
    pBranchAddress: String? = null,
    pIfscCode: String? = null,
    pMicrCode: String? = null,
    pNotes: String? = null,
    pCreationDate: Date = Date(),
) {
    var key = pKey
    var title: ObservableField<String> = ObservableField(pTitle)
    var accountNo: ObservableField<String> = ObservableField(pAccountNo)
    var customerName: ObservableField<String> = ObservableField(pCustomerName)
    var customerId: ObservableField<String> = ObservableField(pCustomerId)
    var branchCode: ObservableField<String?> = ObservableField(pBranchCode)
    var branchName: ObservableField<String> = ObservableField(pBranchName)
    var branchAddress: ObservableField<String?> = ObservableField(pBranchAddress)
    var ifscCode: ObservableField<String> = ObservableField(pIfscCode)
    var micrCode: ObservableField<String> = ObservableField(pMicrCode)
    var notes: ObservableField<String?> = ObservableField(pNotes)
    var creationDate = pCreationDate

    companion object {
        /*
         * converts screen data to db entity data
         * while inserting new data in db, we want current date for creation date
         * while updating data in db, we don't want to update creation date
         * */
        fun BankAccountScreenData.toBankAccountDataEntity(getCurrentDate: Boolean): BankAccountDataEntity {
            return BankAccountDataEntity(
                key,
                title.getValueOrEmpty(),
                accountNo.getValueOrEmpty(),
                customerName.get(),
                customerId.get(),
                branchCode.get(),
                branchName.get(),
                branchAddress.get(),
                ifscCode.get(),
                micrCode.get(),
                notes.get(),
                if (getCurrentDate) Date() else creationDate,
                Date(),
            )
        }

        fun BankAccountDataEntity.toBankAccountScreenData(): BankAccountScreenData {
            return BankAccountScreenData(
                key,
                title,
                accountNumber,
                customerName,
                customerId,
                branchCode,
                branchName, branchAddress, ifscCode, micrCode, notes, creationDate,
            )
        }
    }

    fun updateData(bankAccountScreenData: BankAccountScreenData) {
        key = bankAccountScreenData.key
        title.set(bankAccountScreenData.title.get())
        accountNo.set(bankAccountScreenData.accountNo.get())
        customerName.set(bankAccountScreenData.customerName.get())
        customerId.set(bankAccountScreenData.customerId.get())
        branchCode.set(bankAccountScreenData.branchCode.get())
        branchName.set(bankAccountScreenData.branchName.get())
        branchAddress.set(bankAccountScreenData.branchAddress.get())
        ifscCode.set(bankAccountScreenData.ifscCode.get())
        micrCode.set(bankAccountScreenData.micrCode.get())
        notes.set(bankAccountScreenData.notes.get())
        creationDate = bankAccountScreenData.creationDate
    }
}
