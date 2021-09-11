package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SecureNoteDataDaoSecure @Inject constructor(
    private val secureNoteDataDao: SecureNoteDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : SecureNoteDataDao {
    override suspend fun insertSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity) {
        secureNoteDataDao.insertSecretNoteData(encrypt(secureNoteDataEntity))
    }

    override suspend fun updateSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity) {
        secureNoteDataDao.updateSecretNoteData(encrypt(secureNoteDataEntity))
    }

    override fun getAllSecretNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDao.getAllSecretNoteData()
            .map { SearchSecureNoteData.decrypt(it, symmetricKeyUtils) }
    }

    override suspend fun getSecretNoteDataByKey(key: Int): SecureNoteDataEntity {
        return decrypt(secureNoteDataDao.getSecretNoteDataByKey(key))
    }

    override suspend fun deleteSecretNoteDataByKey(key: Int) {
        secureNoteDataDao.deleteSecretNoteDataByKey(key)
    }

    private fun encrypt(secureNoteDataEntity: SecureNoteDataEntity): SecureNoteDataEntity {
        secureNoteDataEntity.let {
            return SecureNoteDataEntity(
                it.key,
                symmetricKeyUtils.encrypt(it.title),
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
                symmetricKeyUtils.decrypt(it.title),
                symmetricKeyUtils.decrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
