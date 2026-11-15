package com.andryoga.safebox.test.fixtures

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.HashingUtilsImpl
import com.andryoga.safebox.security.SecurityUtils
import com.andryoga.safebox.security.envelope.AndroidKeystoreGateway
import java.util.Date
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Builds a `crypto_version = 1` vault the way a pre-envelope release left it after `MIGRATION_5_6`,
 * now that the app only reads such vaults: the parked password hash, the hint under the legacy key
 * and, for suites that need it, the legacy key itself in the real Android Keystore.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
object LegacyVaultFixtures {
    /** Fixed so a hash is reproducible across runs; old releases drew 16 random bytes. */
    private val FIXED_SALT = ByteArray(16) { it.toByte() }

    /**
     * @return `b64(PBKDF2-SHA1(password))|b64(salt)`, the `legacy_password_hash` format
     * `HashingUtilsImpl.compareHash` reads.
     */
    fun legacyPasswordHash(password: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            FIXED_SALT,
            HashingUtilsImpl.Constants.iterationCount,
            HashingUtilsImpl.Constants.keyLength,
        )
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecurityUtils.encodeBase64(hash) + "|" + SecurityUtils.encodeBase64(FIXED_SALT)
    }

    /**
     * Writes `crypto_version = 1`, the parked hash of [password] and a user row whose hint is
     * encrypted under [legacyKey], in one transaction.
     */
    suspend fun seedLegacyVault(
        database: SafeBoxDatabase,
        legacyKey: SecretKey,
        password: String,
        hint: String,
    ) {
        val now = Date()
        database.withTransaction {
            database.vaultMetaDao()
                .upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
            database.vaultMetaDao()
                .upsert(VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, legacyPasswordHash(password)))
            database.userDetailsDao().insertUserDetailsData(
                UserDetailsEntity(
                    key = 0,
                    uid = UUID.randomUUID().toString(),
                    hint = AesGcmFieldCipher.encrypt(hint, legacyKey),
                    creationDate = now,
                    updateDate = now,
                ),
            )
        }
    }

    /**
     * Generates `symmetricDataKey` in the real Android Keystore with the spec old releases used.
     * Not auth-bound, so it works on images without a secure lock screen.
     */
    fun seedLegacyKeystoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeystoreGateway.ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                AndroidKeystoreGateway.LEGACY_SYMMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}
