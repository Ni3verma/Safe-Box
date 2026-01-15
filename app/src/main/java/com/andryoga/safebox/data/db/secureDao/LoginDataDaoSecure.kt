package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LoginDataDaoSecure @Inject constructor(
    private val loginDataDao: LoginDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : LoginDataDao {
    override suspend fun upsertLoginData(loginDataEntity: LoginDataEntity) {
        loginDataDao.upsertLoginData(encrypt(loginDataEntity))
    }

    override fun insertMultipleLoginData(loginDataEntity: List<LoginDataEntity>) {
        loginDataDao.insertMultipleLoginData(loginDataEntity.map { encrypt(it) })
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
