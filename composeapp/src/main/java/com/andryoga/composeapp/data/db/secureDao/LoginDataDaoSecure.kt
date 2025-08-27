package com.andryoga.composeapp.data.db.secureDao

import com.andryoga.composeapp.common.Utils.decryptNullableString
import com.andryoga.composeapp.common.Utils.encryptNullableString
import com.andryoga.composeapp.data.db.dao.LoginDataDao
import com.andryoga.composeapp.data.db.docs.SearchLoginData
import com.andryoga.composeapp.data.db.docs.export.ExportLoginData
import com.andryoga.composeapp.data.db.entity.LoginDataEntity
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
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

    override fun insertMultipleLoginData(loginDataEntity: List<LoginDataEntity>) {
        loginDataDao.insertMultipleLoginData(loginDataEntity.map { encrypt(it) })
    }

    override suspend fun updateLoginData(loginDataEntity: LoginDataEntity) {
        loginDataDao.updateLoginData(encrypt(loginDataEntity))
    }

    override fun getAllLoginData(): Flow<List<SearchLoginData>> {
        return loginDataDao.getAllLoginData()
            .map { SearchLoginData.decrypt(it, symmetricKeyUtils) }
    }

    override suspend fun getLoginDataByKey(key: Int): LoginDataEntity {
        return decrypt(loginDataDao.getLoginDataByKey(key))
    }

    override suspend fun deleteLoginDataByKey(key: Int) {
        loginDataDao.deleteLoginDataByKey(key)
    }

    override suspend fun exportAllData(): List<ExportLoginData> {
        return loginDataDao.exportAllData().map { decrypt(it) }
    }

    override fun deleteAllData() {
        loginDataDao.deleteAllData()
    }

    private fun encrypt(loginDataEntity: LoginDataEntity): LoginDataEntity {
        loginDataEntity.let {
            return LoginDataEntity(
                it.key,
                it.title,
                it.url.encryptNullableString(symmetricKeyUtils),
                it.password.encryptNullableString(symmetricKeyUtils),
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
                it.title,
                it.url.decryptNullableString(symmetricKeyUtils),
                it.password.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }

    private fun decrypt(exportLoginData: ExportLoginData): ExportLoginData {
        exportLoginData.let {
            return ExportLoginData(
                it.title,
                it.url.decryptNullableString(symmetricKeyUtils),
                it.password.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.userId),
                it.creationDate,
                it.updateDate,
            )
        }
    }
}
