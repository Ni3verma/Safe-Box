package com.andryoga.safebox.data.repository

import androidx.room.withTransaction
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.security.envelope.KdfParams
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/**
 * Reads and writes the vault's envelope metadata (`vault_meta`) and the initial `user_details` row.
 */
class VaultRepositoryImpl @Inject constructor(
    private val database: SafeBoxDatabase,
    private val vaultMetaDao: VaultMetaDao,
    private val userDetailsDao: UserDetailsDao,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
) : VaultRepository {

    override suspend fun hasVault(): Boolean {
        if (vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) != null) return true
        if (userDetailsDao.getUserCount() == 0) return false
        // MIGRATION_5_6 stamps crypto_version for every user row and signup writes both in one
        // transaction, so a user row without it is a defect. Still a vault: Login, never Signup.
        Utils.crashInDebugBuild("user_details row without a crypto_version row")
        return true
    }

    override suspend fun createVault(password: CharArray, hint: String): ByteArray {
        val dek = envelopeEncryptionManager.mintDek()
        val kdfParams = envelopeEncryptionManager.deriveKdfParams()
        val wrappedPassword = envelopeEncryptionManager.wrapWithPassword(password, kdfParams, dek)
        val verifier = envelopeEncryptionManager.makeVerifier(dek)
        val uid = UUID.randomUUID().toString()
        val now = Date()

        database.withTransaction {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.ENVELOPE))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.KDF_PARAMS, kdfParams.encode()))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_PASSWORD, wrappedPassword))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.DEK_VERIFIER, verifier))
            userDetailsDao.insertUserDetailsData(
                UserDetailsEntity(
                    key = 0,
                    uid = uid,
                    hint = hint.ifBlank { null },
                    creationDate = now,
                    updateDate = now,
                ),
            )
        }
        setCrashlyticsUid(uid)
        return dek
    }

    override suspend fun rewrapPassword(newPassword: CharArray, dek: ByteArray) {
        val kdfParams = envelopeEncryptionManager.deriveKdfParams()
        val wrappedPassword = envelopeEncryptionManager.wrapWithPassword(newPassword, kdfParams, dek)
        database.withTransaction {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.KDF_PARAMS, kdfParams.encode()))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_PASSWORD, wrappedPassword))
        }
    }

    override suspend fun storeWrap(spec: WrapKeySpec, blob: String, aliasSuffix: String?) {
        database.withTransaction {
            vaultMetaDao.upsert(VaultMetaEntity(spec.wrappedDekKey, blob))
            if (!aliasSuffix.isNullOrEmpty()) {
                vaultMetaDao.upsert(VaultMetaEntity(spec.aliasSuffixKey, aliasSuffix))
            }
        }
    }

    override suspend fun deleteWrap(spec: WrapKeySpec) {
        vaultMetaDao.delete(spec.wrappedDekKey)
    }

    override suspend fun getWrapKeyAliasSuffix(spec: WrapKeySpec): String? =
        vaultMetaDao.get(spec.aliasSuffixKey)

    override suspend fun getVaultState(): VaultState {
        val version = vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION)
        if (version != CryptoVersion.ENVELOPE) {
            return VaultState.Legacy
        }
        val kdfJson = vaultMetaDao.get(VaultMetaKey.KDF_PARAMS)
        val wrappedPassword = vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_PASSWORD)
        val verifier = vaultMetaDao.get(VaultMetaKey.DEK_VERIFIER)
        if (kdfJson.isNullOrBlank() || wrappedPassword.isNullOrBlank() || verifier.isNullOrBlank()) {
            Utils.crashInDebugBuild("crypto_version = 2 vault missing mandatory vault_meta rows")
            return VaultState.CorruptedEnvelope
        }
        val kdfParams = try {
            KdfParams.decode(kdfJson)
        } catch (e: Exception) {
            Utils.crashInDebugBuild("crypto_version = 2 vault has unparseable kdf_params", e)
            return VaultState.CorruptedEnvelope
        }
        return VaultState.Envelope(
            kdfParams = kdfParams,
            wrappedDekPassword = wrappedPassword,
            dekVerifier = verifier,
            wrappedDekBiometric = vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC),
            biometricKeyAliasSuffix = vaultMetaDao.get(VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX),
            wrappedDekDevice = vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE),
            deviceKeyAliasSuffix = vaultMetaDao.get(VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX),
            isBiometricUnlockDisabled =
                vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) == BIOMETRIC_DISABLED_VALUE,
        )
    }

    private fun setCrashlyticsUid(uid: String) {
        FirebaseCrashlytics.getInstance().apply {
            setUserId(uid)
            setCustomKey(CommonConstants.CRASHLYTICS_KEY_UID, uid)
        }
    }

    private companion object {
        const val BIOMETRIC_DISABLED_VALUE = "1"
    }
}
