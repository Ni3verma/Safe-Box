package com.andryoga.safebox.data.db.secureDao

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

    override suspend fun getUserDetails(): UserDetailsEntity {
        return userDetailsDao.getUserDetails()
    }

    override suspend fun getHint(): String {
        return userDetailsDao.getHint()
    }

    suspend fun checkPassword(password: String): Boolean {
        val userDetailsEntity = getUserDetails()
        return hashingUtils.compareHash(password, userDetailsEntity.password)
    }

    private fun hash(userDetailsEntity: UserDetailsEntity): UserDetailsEntity {
        userDetailsEntity.let {
            return UserDetailsEntity(
                it.key,
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
                it.password,
                it.hint?.let { it1 -> symmetricKeyUtils.encrypt(it1) },
                it.creationDate,
                it.updateDate
            )
        }
    }
}
