package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginDataDaoSecure @Inject constructor(
    private val loginDataDao: LoginDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : LoginDataDao {
    override suspend fun insertLoginData(loginDataEntity: LoginDataEntity) {
        loginDataDao.insertLoginData(encrypt(loginDataEntity))
    }

    override fun getAllLoginData(): Flow<List<LoginDataEntity>> {
        TODO("Not yet implemented")
    }

    override fun getLoginDataByKey(key: Int): Flow<LoginDataEntity> {
        TODO("Not yet implemented")
    }

    private fun encrypt(loginDataEntity: LoginDataEntity): LoginDataEntity {
        loginDataEntity.let {
            return LoginDataEntity(
                symmetricKeyUtils.encrypt(it.title),
                it.url?.let { it1 -> symmetricKeyUtils.encrypt(it1) },
                symmetricKeyUtils.encrypt(it.password),
                it.notes?.let { it1 -> symmetricKeyUtils.encrypt(it1) },
                symmetricKeyUtils.encrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }

    private fun decrypt(loginDataEntity: LoginDataEntity): LoginDataEntity {
        loginDataEntity.let {
            return LoginDataEntity(
                symmetricKeyUtils.decrypt(it.title),
                it.url?.let { it1 -> symmetricKeyUtils.decrypt(it1) },
                symmetricKeyUtils.decrypt(it.password),
                it.notes?.let { it1 -> symmetricKeyUtils.decrypt(it1) },
                symmetricKeyUtils.decrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }
}
