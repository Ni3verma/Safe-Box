package com.andryoga.safebox.data.repository

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.docs.SearchAuthenticatorData
import com.andryoga.safebox.data.db.secureDao.AuthenticatorDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.mappers.record.toAuthenticatorData
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of [AuthenticatorDataRepository] delegating to [AuthenticatorDataDaoSecure]
 * for encrypted persistence and logging analytics events on new creations.
 */
class AuthenticatorDataRepositoryImpl @Inject constructor(
    private val authenticatorDataDaoSecure: AuthenticatorDataDaoSecure,
    private val analyticsHelper: AnalyticsHelper,
) : AuthenticatorDataRepository {
    override suspend fun upsertAuthenticatorData(authenticatorData: AuthenticatorData) {
        if (authenticatorData.id == null || authenticatorData.id == 0) {
            analyticsHelper.logEvent(AnalyticsKey.NEW_AUTHENTICATOR)
        }
        authenticatorDataDaoSecure.upsertAuthenticatorData(authenticatorData.toDbEntity())
    }

    override fun getAllAuthenticatorData(): Flow<List<SearchAuthenticatorData>> {
        return authenticatorDataDaoSecure.getAllAuthenticatorData()
    }

    override suspend fun getAuthenticatorDataByKey(key: Int): AuthenticatorData {
        return authenticatorDataDaoSecure.getAuthenticatorDataByKey(key).toAuthenticatorData()
    }

    override suspend fun deleteAuthenticatorDataByKey(key: Int) {
        authenticatorDataDaoSecure.deleteAuthenticatorDataByKey(key)
    }
}
