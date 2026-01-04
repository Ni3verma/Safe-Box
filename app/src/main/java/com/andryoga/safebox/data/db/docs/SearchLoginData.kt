package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import java.util.Date

data class SearchLoginData(
    val key: Int,
    val title: String,
    val userId: String,
    val creationDate: Date
) {
    companion object {
        fun decrypt(
            searchLoginData: SearchLoginData,
            symmetricKeyUtils: SymmetricKeyUtils
        ): SearchLoginData {
            searchLoginData.let {
                return SearchLoginData(
                    it.key,
                    it.title,
                    symmetricKeyUtils.decrypt(it.userId),
                    it.creationDate
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
