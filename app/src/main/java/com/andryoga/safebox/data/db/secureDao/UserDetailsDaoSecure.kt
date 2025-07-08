package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.security.interfaces.HashingUtils
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import javax.inject.Inject

class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val hashingUtils: HashingUtils,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : UserDetailsDao {
    override suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity) {
        var entity = hash(userDetailsEntity)
        entity = encrypt(entity)
        userDetailsDao.insertUserDetailsData(entity)
    }

    override suspend fun getUserPassword(): String {
        return userDetailsDao.getUserPassword()
    }

    override suspend fun getHint(): String? {
        return userDetailsDao.getHint()?.let { symmetricKeyUtils.decrypt(it) }
    }

    override suspend fun getUid(): String {
        return userDetailsDao.getUid()
    }

    suspend fun checkPassword(password: String): Boolean {
        return hashingUtils.compareHash(password, getUserPassword())
    }

    private fun hash(userDetailsEntity: UserDetailsEntity): UserDetailsEntity {
        userDetailsEntity.let {
            return UserDetailsEntity(
                it.key,
                it.uid,
                hashingUtils.hash(it.password),
                it.hint,
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun encrypt(userDetailsEntity: UserDetailsEntity): UserDetailsEntity {
        userDetailsEntity.let {
            return UserDetailsEntity(
                it.key,
                it.uid,
                it.password,
                it.hint.encryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
