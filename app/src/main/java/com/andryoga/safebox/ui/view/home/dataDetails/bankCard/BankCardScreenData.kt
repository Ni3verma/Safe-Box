package com.andryoga.safebox.ui.view.home.dataDetails.bankCard

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class BankCardScreenData(
    /*
   * It is very important to initialize key with 0
   * so that when we convert screen data to entity for db insertion at that
   * 0 will be passed. For room zero means that it can auto-increment value
   * */
    pKey: Int = 0,
    pTitle: String = "",
    pName: String? = null,
    pNumber: String = "",
    pExpiryDate: String? = null,
    pPin: String? = null,
    pCvv: String? = null,
    pNotes: String? = null,
    pCreationDate: Date = Date()
) {
    var key = pKey
    var title: ObservableField<String> = ObservableField(pTitle)
    var name: ObservableField<String?> = ObservableField(pName)
    var number: ObservableField<String> = ObservableField(pNumber)
    var expiryDate: ObservableField<String> = ObservableField(pExpiryDate)
    var pin: ObservableField<String?> = ObservableField(pPin)
    var cvv: ObservableField<String> = ObservableField(pCvv)
    var notes: ObservableField<String?> = ObservableField(pNotes)
    var creationDate = pCreationDate

    companion object {
        fun BankCardScreenData.toBankCardDataEntity(): BankCardDataEntity {
            return BankCardDataEntity(
                key,
                title.getValueOrEmpty(),
                name.get(),
                number.getValueOrEmpty(),
                pin.get(),
                cvv.get(),
                expiryDate.get(),
                notes.get(),
                creationDate,
                Date()
            )
        }

        fun BankCardDataEntity.toBankCardScreenData(): BankCardScreenData {
            return BankCardScreenData(
                key, title, name, number, expiryDate, pin, cvv, notes, creationDate
            )
        }
    }

    fun updateData(bankCardScreenData: BankCardScreenData) {
        key = bankCardScreenData.key
        title.set(bankCardScreenData.title.get())
        name.set(bankCardScreenData.name.get())
        number.set(bankCardScreenData.number.get())
        expiryDate.set(bankCardScreenData.expiryDate.get())
        pin.set(bankCardScreenData.pin.get())
        cvv.set(bankCardScreenData.cvv.get())
        notes.set(bankCardScreenData.notes.get())
        creationDate = bankCardScreenData.creationDate
    }
}
