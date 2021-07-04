package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

data class SearchLoginData(
    val key: Int,
    val title: String,
    val userId: String
) {
    companion object {
        fun decrypt(
            searchLoginData: SearchLoginData,
            symmetricKeyUtils: SymmetricKeyUtils
        ): SearchLoginData {
            searchLoginData.let {
                return SearchLoginData(
                    it.key,
                    symmetricKeyUtils.decrypt(it.title),
                    symmetricKeyUtils.decrypt(it.userId),
                )
            }
        }

        fun decrypt(
            searchLoginData: List<SearchLoginData>,
            symmetricKeyUtils: SymmetricKeyUtils
        ): List<SearchLoginData> {
            return searchLoginData.map { decrypt(it, symmetricKeyUtils) }
        }
    }
}
