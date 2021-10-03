package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

data class SearchBankAccountData(
    val key: Int,
    val title: String,
    val accountNumber: String
) {
    companion object {
        fun decrypt(
            searchBankAccountData: SearchBankAccountData,
            symmetricKeyUtils: SymmetricKeyUtils
        ): SearchBankAccountData {
            searchBankAccountData.let {
                // mask account number except for last 4 digits
                val accountNumber = symmetricKeyUtils.decrypt(it.accountNumber)
                return SearchBankAccountData(
                    it.key,
                    it.title,
                    accountNumber.replace(Regex(".(?=.{4})"), "X")
                )
            }
        }

        fun decrypt(
            searchBankAccountData: List<SearchBankAccountData>,
            symmetricKeyUtils: SymmetricKeyUtils
        ): List<SearchBankAccountData> {
            return searchBankAccountData.map { decrypt(it, symmetricKeyUtils) }
        }
    }
}
