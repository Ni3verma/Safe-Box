package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

data class SearchSecureNoteData(
    val key: Int,
    val title: String
) {
    companion object {
        fun decrypt(
            searchSecureNoteData: SearchSecureNoteData,
            symmetricKeyUtils: SymmetricKeyUtils
        ): SearchSecureNoteData {
            searchSecureNoteData.let {
                return SearchSecureNoteData(
                    it.key,
                    symmetricKeyUtils.decrypt(it.title)
                )
            }
        }

        fun decrypt(
            searchSecureNoteData: List<SearchSecureNoteData>,
            symmetricKeyUtils: SymmetricKeyUtils
        ): List<SearchSecureNoteData> {
            return searchSecureNoteData.map { decrypt(it, symmetricKeyUtils) }
        }
    }
}
