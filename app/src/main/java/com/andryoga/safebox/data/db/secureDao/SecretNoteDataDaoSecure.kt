package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.SecretNoteDataDao
import com.andryoga.safebox.data.db.entity.SecretNoteDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SecretNoteDataDaoSecure @Inject constructor(
    private val secretNoteDataDao: SecretNoteDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : SecretNoteDataDao {
    override suspend fun insertSecretNoteData(secretNoteDataEntity: SecretNoteDataEntity) {
        secretNoteDataDao.insertSecretNoteData(encrypt(secretNoteDataEntity))
    }

    override fun getAllSecretNoteData(): Flow<List<SecretNoteDataEntity>> {
        TODO("Not yet implemented")
    }

    override fun getSecretNoteDataByKey(key: Int): Flow<SecretNoteDataEntity> {
        TODO("Not yet implemented")
    }

    private fun encrypt(secretNoteDataEntity: SecretNoteDataEntity): SecretNoteDataEntity {
        secretNoteDataEntity.let {
            return SecretNoteDataEntity(
                symmetricKeyUtils.encrypt(it.title),
                symmetricKeyUtils.encrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(secretNoteDataEntity: SecretNoteDataEntity): SecretNoteDataEntity {
        secretNoteDataEntity.let {
            return SecretNoteDataEntity(
                symmetricKeyUtils.decrypt(it.title),
                symmetricKeyUtils.decrypt(it.notes),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
