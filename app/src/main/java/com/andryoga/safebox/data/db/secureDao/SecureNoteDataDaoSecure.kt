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

    override fun getAllSecretNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDao.getAllSecretNoteData()
            .map { SearchSecureNoteData.decrypt(it, symmetricKeyUtils) }
    }

    override fun getSecretNoteDataByKey(key: Int): Flow<SecureNoteDataEntity> {
        TODO("Not yet implemented")
    }

    private fun encrypt(secureNoteDataEntity: SecureNoteDataEntity): SecureNoteDataEntity {
        secureNoteDataEntity.let {
            return SecureNoteDataEntity(
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
                symmetricKeyUtils.decrypt(it.title),
                symmetricKeyUtils.decrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
