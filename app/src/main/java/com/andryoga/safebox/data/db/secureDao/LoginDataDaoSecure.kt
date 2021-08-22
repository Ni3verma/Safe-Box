package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ExperimentalCoroutinesApi
class LoginDataDaoSecure @Inject constructor(
    private val loginDataDao: LoginDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : LoginDataDao {
    override suspend fun insertLoginData(loginDataEntity: LoginDataEntity) {
        loginDataDao.insertLoginData(encrypt(loginDataEntity))
    }

    override fun getAllLoginData(): Flow<List<SearchLoginData>> {
        return loginDataDao.getAllLoginData()
            .map { SearchLoginData.decrypt(it, symmetricKeyUtils) }
    }

    override suspend fun getLoginDataByKey(key: Int): LoginDataEntity {
        return decrypt(loginDataDao.getLoginDataByKey(key))
    }

    private fun encrypt(loginDataEntity: LoginDataEntity): LoginDataEntity {
        loginDataEntity.let {
            return LoginDataEntity(
                symmetricKeyUtils.encrypt(it.title),
                it.url.encryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.encrypt(it.password),
                it.notes.encryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.encrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }

    private fun decrypt(loginDataEntity: LoginDataEntity): LoginDataEntity {
        loginDataEntity.let {
            return LoginDataEntity(
                it.key,
                symmetricKeyUtils.decrypt(it.title),
                it.url.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.password),
                it.notes.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }

    private fun decrypt(loginDataEntities: List<LoginDataEntity>): List<LoginDataEntity> {
        return loginDataEntities.map { decrypt(it) }
    }
}
