package com.andryoga.safebox.data.db.docs

import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.totp.models.TotpAlgorithm
import java.util.Date

/**
 * Lightweight projection of [AuthenticatorDataEntity] used for record lists and searching.
 *
 * Carries the generation parameters alongside the seed because the records list renders a live
 * code for every authenticator row, which needs them to be correct.
 */
data class SearchAuthenticatorData(
    val key: Int,
    val title: String,
    val secretKey: String,
    val algorithm: TotpAlgorithm,
    val digits: Int,
    val period: Int,
    val creationDate: Date,
) {
    companion object {
        fun decrypt(
            searchAuthenticatorData: SearchAuthenticatorData,
            symmetricKeyUtils: SymmetricKeyUtils,
        ): SearchAuthenticatorData {
            return searchAuthenticatorData.copy(
                secretKey = symmetricKeyUtils.decrypt(searchAuthenticatorData.secretKey),
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
