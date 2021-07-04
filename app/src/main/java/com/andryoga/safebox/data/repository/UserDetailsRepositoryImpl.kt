package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import java.util.*
import javax.inject.Inject

class UserDetailsRepositoryImpl @Inject constructor(
    private val userDetailsDaoSecure: UserDetailsDaoSecure
) : UserDetailsRepository {
    override suspend fun insertUserDetailsData(password: String, hint: String) {
        val entity = UserDetailsEntity(
            password, hint, Date(), Date()
        )
        userDetailsDaoSecure.insertUserDetailsData(entity)
    }

    override suspend fun checkPassword(password: String): Boolean {
        return userDetailsDaoSecure.checkPassword(password)
    }

    override suspend fun getHint(): String {
        return userDetailsDaoSecure.getHint()
    }
}
