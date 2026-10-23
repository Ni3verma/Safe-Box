package com.andryoga.safebox.data.repository

import androidx.room.withTransaction
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.interfaces.HashingUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class UserDetailsRepositoryImpl @Inject constructor(
    private val database: SafeBoxDatabase,
    private val userDetailsDaoSecure: UserDetailsDaoSecure,
    private val vaultMetaDao: VaultMetaDao,
    private val hashingUtils: HashingUtils,
    private val preferenceProvider: PreferenceProvider,
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
) : UserDetailsRepository {
    // TODO(MR5): signup creates a crypto_version = 2 vault through VaultRepository.createVault; until then every new vault is legacy
    override suspend fun insertUserDetailsData(password: String, hint: String) {
        val uid = UUID.randomUUID().toString()
        setCrashlyticsUid(uid)

        val entity = UserDetailsEntity(
            key = 0,
            uid = uid,
            hint = hint,
            creationDate = Date(),
            updateDate = Date(),
        )
        database.withTransaction {
            // crypto_version first: UserDetailsDaoSecure reads it to pick the hint's stored form.
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
            vaultMetaDao.upsert(
                VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, hashingUtils.hash(password)),
            )
            userDetailsDaoSecure.insertUserDetailsData(entity)
        }
    }

    // TODO(MR5): only the legacy login path calls this; a crypto_version = 2 vault unlocks through EnvelopeEncryptionManager (design §6.3)
    // TODO(D3): legacy vault (crypto_version = 1) only — the legacy credential check
    override suspend fun checkPassword(password: String): Boolean {
        val legacyPasswordHash = vaultMetaDao.get(VaultMetaKey.LEGACY_PASSWORD_HASH)
        if (legacyPasswordHash == null) {
            // The login screen is only reachable after signup or MIGRATION_5_6 wrote the hash.
            Utils.crashInDebugBuild("legacy_password_hash missing on the password login path")
            return false
        }
        return hashingUtils.compareHash(password, legacyPasswordHash)
    }

    override suspend fun onAuthSuccess(withBiometric: Boolean): Int {
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

        // the pref defaults to 1 and is stored post-increment, so this read is the login's ordinal
        val currLoginCount = preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
        preferenceProvider.upsertIntPref(
            CommonConstants.TOTAL_LOGIN_COUNT,
            currLoginCount + 1
        )

        Timber.i(
            "setting biometric login count remaining to: $newBiometricLoginCountRemaining" +
                    " and total login count to: ${currLoginCount + 1}"
        )
        return currLoginCount
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

    // TODO(MR7): a crypto_version = 2 vault re-wraps the DEK through VaultRepository.rewrapPassword (design §6.7, §6.8) and never writes a legacy hash
    override suspend fun updatePasswordAndHint(newPassword: String, hint: String) {
        Timber.i("updating master password and hint")
        database.withTransaction {
            vaultMetaDao.upsert(
                VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, hashingUtils.hash(newPassword)),
            )
            userDetailsDaoSecure.updateHint(hint, Date())
        }

        val newBiometricLoginCountRemaining = settingsDataStore.getPasswordAfterXBiometricLogins()
        preferenceProvider.upsertIntPref(
            CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
            newBiometricLoginCountRemaining
        )
    }

    private fun setCrashlyticsUid(uid: String) {
        FirebaseCrashlytics.getInstance().apply {
            setUserId(uid)
            setCustomKey(CommonConstants.CRASHLYTICS_KEY_UID, uid)
        }
    }
}
