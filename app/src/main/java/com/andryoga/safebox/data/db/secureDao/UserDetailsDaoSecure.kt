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
        userDetailsDao.insertUserDetailsData(hash(userDetailsEntity))
        userDetailsDao.insertUserDetailsData(encrypt(userDetailsEntity))
    }

    override suspend fun getUserDetails(): UserDetailsEntity {
        return userDetailsDao.getUserDetails()
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
                it.hint,
                symmetricKeyUtils.encrypt(it.hint),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
