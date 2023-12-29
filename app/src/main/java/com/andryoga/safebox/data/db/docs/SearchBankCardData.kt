package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import java.util.*

data class SearchBankCardData(
    val key: Int,
    val title: String,
    val number: String,
    val creationDate: Date,
) {
    companion object {
        fun decrypt(
            searchBankCardData: SearchBankCardData,
            symmetricKeyUtils: SymmetricKeyUtils,
        ): SearchBankCardData {
            searchBankCardData.let {
                // mask card number except for last 4 digits
                val cardNumber = symmetricKeyUtils.decrypt(it.number)
                return SearchBankCardData(
                    it.key,
                    it.title,
                    cardNumber.replace(Regex(".(?=.{4})"), "X"),
                    it.creationDate,
                )
            }
        }

        fun decrypt(
            searchBankCardData: List<SearchBankCardData>,
            symmetricKeyUtils: SymmetricKeyUtils,
        ): List<SearchBankCardData> {
            return searchBankCardData.map { decrypt(it, symmetricKeyUtils) }
        }
    }
}
