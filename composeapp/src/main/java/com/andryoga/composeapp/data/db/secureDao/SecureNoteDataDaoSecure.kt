package com.andryoga.composeapp.data.db.secureDao

import com.andryoga.composeapp.data.db.dao.SecureNoteDataDao
import com.andryoga.composeapp.data.db.docs.SearchSecureNoteData
import com.andryoga.composeapp.data.db.docs.export.ExportSecureNoteData
import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SecureNoteDataDaoSecure @Inject constructor(
    private val secureNoteDataDao: SecureNoteDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : SecureNoteDataDao {
    override suspend fun insertSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity) {
        secureNoteDataDao.insertSecretNoteData(encrypt(secureNoteDataEntity))
    }

    override fun insertMultipleSecureNoteData(secureNoteDataEntity: List<SecureNoteDataEntity>) {
        secureNoteDataDao.insertMultipleSecureNoteData(secureNoteDataEntity.map { encrypt(it) })
    }

    override suspend fun updateSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity) {
        secureNoteDataDao.updateSecretNoteData(encrypt(secureNoteDataEntity))
    }

    override fun getAllSecretNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDao.getAllSecretNoteData()
    }

    override suspend fun getSecretNoteDataByKey(key: Int): SecureNoteDataEntity {
        return decrypt(secureNoteDataDao.getSecretNoteDataByKey(key))
    }

    override suspend fun deleteSecretNoteDataByKey(key: Int) {
        secureNoteDataDao.deleteSecretNoteDataByKey(key)
    }

    override suspend fun exportAllData(): List<ExportSecureNoteData> {
        return secureNoteDataDao.exportAllData().map { decrypt(it) }
    }

    override fun deleteAllData() {
        secureNoteDataDao.deleteAllData()
    }

    private fun encrypt(secureNoteDataEntity: SecureNoteDataEntity): SecureNoteDataEntity {
        secureNoteDataEntity.let {
            return SecureNoteDataEntity(
                it.key,
                it.title,
                symmetricKeyUtils.encrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(secureNoteDataEntity: SecureNoteDataEntity): SecureNoteDataEntity {
        secureNoteDataEntity.let {
            return SecureNoteDataEntity(
                it.key,
                it.title,
                symmetricKeyUtils.decrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(exportSecureNoteData: ExportSecureNoteData): ExportSecureNoteData {
        exportSecureNoteData.let {
            return ExportSecureNoteData(
                it.title,
                symmetricKeyUtils.decrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
