package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.security.SymmetricKeyUtils
import javax.inject.Inject

class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : UserDetailsDao {
    override suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity) {
        userDetailsDao.insertUserDetailsData(encrypt(userDetailsEntity))
    }

    override suspend fun checkPassword(password: String): Int {
        return userDetailsDao.checkPassword(symmetricKeyUtils.encrypt(password))
    }

    private fun encrypt(userDetailsEntity: UserDetailsEntity): UserDetailsEntity {
        userDetailsEntity.let {
            return UserDetailsEntity(
                it.key,
                symmetricKeyUtils.encrypt(it.password),
                symmetricKeyUtils.encrypt(it.hint),
                it.creationDate,
                it.updateDate
            )
        }
    }
}