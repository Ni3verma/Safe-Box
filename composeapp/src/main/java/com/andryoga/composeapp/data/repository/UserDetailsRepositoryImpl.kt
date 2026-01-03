package com.andryoga.composeapp.data.repository

import com.andryoga.composeapp.common.CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING
import com.andryoga.composeapp.common.CommonConstants.CRASHLYTICS_KEY_UID
import com.andryoga.composeapp.common.CommonConstants.TOTAL_LOGIN_COUNT
import com.andryoga.composeapp.data.db.entity.UserDetailsEntity
import com.andryoga.composeapp.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.composeapp.data.repository.UserDetailsRepositoryImpl.Constants.MAX_CONT_BIOMETRIC_LOGINS
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.providers.interfaces.PreferenceProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class UserDetailsRepositoryImpl @Inject constructor(
    private val userDetailsDaoSecure: UserDetailsDaoSecure,
    private val preferenceProvider: PreferenceProvider,
) : UserDetailsRepository {
    override suspend fun insertUserDetailsData(password: String, hint: String?) {
        val uid = UUID.randomUUID().toString()
        setCrashlyticsUid(uid)

        val entity = UserDetailsEntity(
            password,
            uid,
            hint,
            Date(),
            Date()
        )
        userDetailsDaoSecure.insertUserDetailsData(entity)
    }

    override suspend fun checkPassword(password: String): Boolean {
        return userDetailsDaoSecure.checkPassword(password)
    }

    override suspend fun onAuthSuccess(withBiometric: Boolean) {
        Timber.i("auth success: with biometric: $withBiometric, setting crashlytics user id")
        setCrashlyticsUid(userDetailsDaoSecure.getUid())

        val newBiometricLoginCountRemaining = if (withBiometric) {
            val currentBiometricLoginCountRemaining: Int =
                preferenceProvider.getIntPref(
                    ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                    MAX_CONT_BIOMETRIC_LOGINS
                )

            max(0, currentBiometricLoginCountRemaining - 1)
        } else {
            MAX_CONT_BIOMETRIC_LOGINS
        }

        preferenceProvider.upsertIntPref(
            ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
            max(0, newBiometricLoginCountRemaining)
        )

        val currLoginCount = preferenceProvider.getIntPref(TOTAL_LOGIN_COUNT, 1)
        preferenceProvider.upsertIntPref(
            TOTAL_LOGIN_COUNT,
            currLoginCount + 1
        )

        Timber.i(
            "setting biometric login count remaining to: $newBiometricLoginCountRemaining" +
                    " and total login count to: ${currLoginCount + 1}"
        )
    }

    override suspend fun getHint(): String? {
        return userDetailsDaoSecure.getHint()
    }

    override suspend fun shouldStartBiometricAuthFlow(): Boolean {
        val biometricLoginCountRemaining: Int =
            preferenceProvider.getIntPref(
                ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                Constants.MAX_CONT_BIOMETRIC_LOGINS
            )

        val result = biometricLoginCountRemaining > 0
        Timber.i("should start biometric auth flow: $result")
        return result
    }

    private fun setCrashlyticsUid(uid: String) {
        FirebaseCrashlytics.getInstance().apply {
            setUserId(uid)
            setCustomKey(CRASHLYTICS_KEY_UID, uid)
        }
    }

    object Constants {
        const val MAX_CONT_BIOMETRIC_LOGINS = 5
    }
}
