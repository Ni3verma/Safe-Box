package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class UserDetailsRepositoryImpl @Inject constructor(
    private val userDetailsDaoSecure: UserDetailsDaoSecure,
    private val preferenceProvider: PreferenceProvider,
    private val settingsDataStore: SettingsDataStore,
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
                    CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                    SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
                )

            max(0, currentBiometricLoginCountRemaining - 1)
        } else {
            // reset login count remaining after password login
            settingsDataStore.getPasswordAfterXBiometricLogins()
        }

        preferenceProvider.upsertIntPref(
            CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
            max(0, newBiometricLoginCountRemaining)
        )

        val currLoginCount = preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
        preferenceProvider.upsertIntPref(
            CommonConstants.TOTAL_LOGIN_COUNT,
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
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
            )

        val result = biometricLoginCountRemaining > 0
        Timber.i("should start biometric auth flow: $result")
        return result
    }

    private fun setCrashlyticsUid(uid: String) {
        FirebaseCrashlytics.getInstance().apply {
            setUserId(uid)
            setCustomKey(CommonConstants.CRASHLYTICS_KEY_UID, uid)
        }
    }
}
