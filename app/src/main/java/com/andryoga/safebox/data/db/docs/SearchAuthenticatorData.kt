package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import java.util.Date

/**
 * Lightweight projection of [AuthenticatorDataEntity] used for record lists and searching.
 */
data class SearchAuthenticatorData(
    val key: Int,
    val title: String,
    val secretKey: String,
    val creationDate: Date,
) {
    companion object {
        fun decrypt(
            searchAuthenticatorData: SearchAuthenticatorData,
            symmetricKeyUtils: SymmetricKeyUtils,
        ): SearchAuthenticatorData {
            return SearchAuthenticatorData(
                key = searchAuthenticatorData.key,
                title = searchAuthenticatorData.title,
                secretKey = symmetricKeyUtils.decrypt(searchAuthenticatorData.secretKey),
                creationDate = searchAuthenticatorData.creationDate,
            )
        }

        fun decrypt(
            searchAuthenticatorData: List<SearchAuthenticatorData>,
            symmetricKeyUtils: SymmetricKeyUtils,
        ): List<SearchAuthenticatorData> {
            return searchAuthenticatorData.map { decrypt(it, symmetricKeyUtils) }
        }
    }
}
