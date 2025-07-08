package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.CommonConstants.CRASHLYTICS_KEY_UID
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class UserDetailsRepositoryImpl
@Inject
constructor(
    private val userDetailsDaoSecure: UserDetailsDaoSecure,
) : UserDetailsRepository {
    override suspend fun insertUserDetailsData(
        password: String,
        hint: String?,
    ) {
        val uid = UUID.randomUUID().toString()
        setCrashlyticsUid(uid)

        val entity =
            UserDetailsEntity(
                password,
                uid,
                hint,
                Date(),
                Date(),
            )
        userDetailsDaoSecure.insertUserDetailsData(entity)
    }

    override suspend fun checkPassword(password: String): Boolean {
        val isPasswordCorrect = userDetailsDaoSecure.checkPassword(password)
        if (isPasswordCorrect) {
            Timber.i("password was correct, setting crashlytics user id")
            setCrashlyticsUid(userDetailsDaoSecure.getUid())
        }

        return isPasswordCorrect
    }

    override suspend fun getHint(): String? {
        return userDetailsDaoSecure.getHint()
    }

    private fun setCrashlyticsUid(uid: String) {
        FirebaseCrashlytics.getInstance().apply {
            setUserId(uid)
            setCustomKey(CRASHLYTICS_KEY_UID, uid)
        }
    }
}
