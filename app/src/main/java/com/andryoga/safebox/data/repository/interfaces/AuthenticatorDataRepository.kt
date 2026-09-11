package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchAuthenticatorData
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface defining operations for managing 2FA TOTP authenticator records.
 */
interface AuthenticatorDataRepository {
    suspend fun upsertAuthenticatorData(authenticatorData: AuthenticatorData)
    fun getAllAuthenticatorData(): Flow<List<SearchAuthenticatorData>>
    suspend fun getAuthenticatorDataByKey(key: Int): AuthenticatorData
    suspend fun deleteAuthenticatorDataByKey(key: Int)
}
