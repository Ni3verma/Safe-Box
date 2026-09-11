package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.AuthenticatorDataDao
import com.andryoga.safebox.data.db.docs.SearchAuthenticatorData
import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Decorator for [AuthenticatorDataDao] that encrypts sensitive TOTP secrets on storage
 * and decrypts them upon retrieval via [SymmetricKeyUtils].
 */
class AuthenticatorDataDaoSecure @Inject constructor(
    private val authenticatorDataDao: AuthenticatorDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils,
) : AuthenticatorDataDao {
    override suspend fun upsertAuthenticatorData(authenticatorDataEntity: AuthenticatorDataEntity) {
        authenticatorDataDao.upsertAuthenticatorData(encrypt(authenticatorDataEntity))
    }

    override fun insertMultipleAuthenticatorData(authenticatorDataEntity: List<AuthenticatorDataEntity>) {
        authenticatorDataDao.insertMultipleAuthenticatorData(authenticatorDataEntity.map {
            encrypt(
                it
            )
        })
    }

    override fun getAllAuthenticatorData(): Flow<List<SearchAuthenticatorData>> {
        return authenticatorDataDao.getAllAuthenticatorData()
            .map { SearchAuthenticatorData.decrypt(it, symmetricKeyUtils) }
    }

    override suspend fun getAuthenticatorDataByKey(key: Int): AuthenticatorDataEntity {
        return decrypt(authenticatorDataDao.getAuthenticatorDataByKey(key))
    }

    override suspend fun deleteAuthenticatorDataByKey(key: Int) {
        authenticatorDataDao.deleteAuthenticatorDataByKey(key)
    }

    override suspend fun exportAllData(): List<ExportAuthenticatorData> {
        return authenticatorDataDao.exportAllData().map { decrypt(it) }
    }

    override fun deleteAllData() {
        authenticatorDataDao.deleteAllData()
    }

    private fun encrypt(authenticatorDataEntity: AuthenticatorDataEntity): AuthenticatorDataEntity {
        return AuthenticatorDataEntity(
            key = authenticatorDataEntity.key,
            title = authenticatorDataEntity.title,
            secretKey = symmetricKeyUtils.encrypt(authenticatorDataEntity.secretKey),
            creationDate = authenticatorDataEntity.creationDate,
            updateDate = authenticatorDataEntity.updateDate,
        )
    }

    private fun decrypt(authenticatorDataEntity: AuthenticatorDataEntity): AuthenticatorDataEntity {
        return AuthenticatorDataEntity(
            key = authenticatorDataEntity.key,
            title = authenticatorDataEntity.title,
            secretKey = symmetricKeyUtils.decrypt(authenticatorDataEntity.secretKey),
            creationDate = authenticatorDataEntity.creationDate,
            updateDate = authenticatorDataEntity.updateDate,
        )
    }

    private fun decrypt(exportAuthenticatorData: ExportAuthenticatorData): ExportAuthenticatorData {
        return ExportAuthenticatorData(
            title = exportAuthenticatorData.title,
            secretKey = symmetricKeyUtils.decrypt(exportAuthenticatorData.secretKey),
            creationDate = exportAuthenticatorData.creationDate,
            updateDate = exportAuthenticatorData.updateDate,
        )
    }
}
