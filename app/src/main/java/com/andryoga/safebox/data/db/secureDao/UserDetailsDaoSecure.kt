package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.security.interfaces.HashingUtils
import javax.inject.Inject

class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val hashingUtils: HashingUtils
) : UserDetailsDao {
    override suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity) {
        userDetailsDao.insertUserDetailsData(hash(userDetailsEntity))
    }

    override suspend fun getUserDetails(): UserDetailsEntity {
        return userDetailsDao.getUserDetails()
    }

    suspend fun checkPassword(password: String): Boolean {
        val userDetailsEntity = getUserDetails()
        return hashingUtils.compareHash(password, userDetailsEntity.password)
    }

    private suspend fun hash(userDetailsEntity: UserDetailsEntity): UserDetailsEntity {
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
}
