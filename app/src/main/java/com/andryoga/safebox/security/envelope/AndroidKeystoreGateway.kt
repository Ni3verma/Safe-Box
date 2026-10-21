package com.andryoga.safebox.security.envelope

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [KeystoreGateway] over `AndroidKeyStore`.
 *
 * Wrap keys are RSA-2048 OAEP pairs generated StrongBox-first. Every private-key cipher is
 * initialised with the explicit MGF1-SHA1 parameters of [OAEP_SPEC], the only combination that
 * interoperates with the JCA encrypt side on every supported API (ADR-0011 S4). The legacy
 * `symmetricDataKey` is read but never regenerated here.
 *
 * The decisions (failure mapping, StrongBox retry, probe classification) are pure companion
 * functions so they are unit-tested on the JVM; the Keystore calls themselves are covered on a
 * device by `KeystoreWrapKeysTest`.
 */
@Singleton
class AndroidKeystoreGateway @Inject constructor() : KeystoreGateway {

    override fun generateWrapKeyPair(alias: String, spec: WrapKeySpec): PublicKey {
        deleteKey(alias)
        return try {
            withStrongBoxFallback { strongBoxBacked ->
                val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                generator.initialize(buildKeyGenParameterSpec(alias, spec, strongBoxBacked))
                generator.generateKeyPair().public
            }
        } catch (e: Exception) {
            throw mapKeyGenFailure(e, spec)
        }
    }

    override fun publicKey(alias: String): PublicKey? = keyStore().getCertificate(alias)?.publicKey

    override fun decryptCipher(alias: String): Cipher {
        val privateKey = try {
            keyStore().getKey(alias, null) as? PrivateKey
        } catch (e: GeneralSecurityException) {
            // UnrecoverableKeyException after a lock-screen removal, KeyStoreException on a stuck entry (ADR-0011 S2/S9).
            throw KeyPermanentlyInvalidatedException("Wrap key $alias cannot be loaded", e)
        }
        if (privateKey == null) {
            throw KeyPermanentlyInvalidatedException("Wrap key $alias is missing")
        }
        return Cipher.getInstance(RSA_OAEP_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        }
    }

    override fun probe(alias: String): KeyHealth {
        val privateKey = try {
            keyStore().getKey(alias, null) as? PrivateKey
        } catch (e: Exception) {
            return healthFromFailure(e)
        }
        if (privateKey == null) {
            return if (aliasExists(alias)) KeyHealth.Invalidated else KeyHealth.Missing
        }
        return try {
            Cipher.getInstance(RSA_OAEP_TRANSFORMATION).init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
            KeyHealth.Healthy
        } catch (_: UserNotAuthenticatedException) {
            // A time-bound key outside its window (API 29) refuses init but is healthy.
            KeyHealth.Healthy
        } catch (e: Exception) {
            healthFromFailure(e)
        }
    }

    override fun deleteKey(alias: String) {
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            // Non-fatal by design: API 30 can refuse to delete an entry after a lock-screen removal (ADR-0011 S2).
            Timber.w(e, "Keystore entry could not be deleted")
        }
    }

    override fun legacyVaultKey(): SecretKey? =
        keyStore().getKey(LEGACY_SYMMETRIC_KEY_ALIAS, null) as? SecretKey

    override fun deleteLegacyVaultKey() {
        deleteKey(LEGACY_SYMMETRIC_KEY_ALIAS)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun aliasExists(alias: String): Boolean = try {
        keyStore().containsAlias(alias)
    } catch (_: Exception) {
        false
    }

    private fun buildKeyGenParameterSpec(
        alias: String,
        spec: WrapKeySpec,
        strongBoxBacked: Boolean,
    ): KeyGenParameterSpec = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(RSA_KEY_SIZE_BITS)
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .setUserAuthenticationRequired(true)
        .apply {
            when (spec) {
                WrapKeySpec.BIOMETRIC -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                    setInvalidatedByBiometricEnrollment(true)
                }
                WrapKeySpec.DEVICE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(DEVICE_KEY_VALIDITY_SECONDS)
                    }
                }
            }
            setIsStrongBoxBacked(strongBoxBacked)
        }
        .build()

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val LEGACY_SYMMETRIC_KEY_ALIAS = "symmetricDataKey"
        const val RSA_KEY_SIZE_BITS = 2048

        /** Validity window of the time-bound `KEK_Device` on API 29 (design §2.3). */
        const val DEVICE_KEY_VALIDITY_SECONDS = 5
        const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        /** Keystore pads OAEP with MGF1-SHA1 while Conscrypt defaults to MGF1-SHA256; both sides must say SHA1 explicitly. */
        val OAEP_SPEC = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

        /**
         * Runs [generate] StrongBox-backed first and once more without StrongBox when the device
         * has none ([StrongBoxUnavailableException], ADR-0011 S4); any other failure propagates.
         */
        internal fun <T> withStrongBoxFallback(generate: (strongBoxBacked: Boolean) -> T): T = try {
            generate(true)
        } catch (e: Exception) {
            if (e is StrongBoxUnavailableException || e.cause is StrongBoxUnavailableException) {
                generate(false)
            } else {
                throw e
            }
        }

        /**
         * Maps the `IllegalStateException` Keystore raises for a missing lock screen or biometric —
         * thrown directly or as the cause of an `InvalidAlgorithmParameterException` (ADR-0011 S5) —
         * to the typed exceptions of [KeystoreGateway]. Anything else is returned unchanged for the
         * caller to handle.
         */
        internal fun mapKeyGenFailure(error: Exception, spec: WrapKeySpec): Exception {
            val illegalState = error as? IllegalStateException
                ?: error.cause as? IllegalStateException
                ?: return error
            val message = illegalState.message.orEmpty()
            return when {
                message.contains("biometric", ignoreCase = true) -> BiometricNotEnrolledException(error)
                message.contains("lock screen", ignoreCase = true) -> LockScreenRequiredException(error)
                spec == WrapKeySpec.BIOMETRIC -> BiometricNotEnrolledException(error)
                else -> LockScreenRequiredException(error)
            }
        }

        /**
         * Every probe failure means "recreate the wrap" (design §6.10). Crypto-API failures vary by
         * OEM and are expected; any other class is a programming error and is flagged.
         */
        internal fun healthFromFailure(error: Exception): KeyHealth {
            if (error !is GeneralSecurityException && error !is ProviderException) {
                Utils.crashInDebugBuild("Keystore probe failed outside the crypto API", error)
            }
            return KeyHealth.Invalidated
        }
    }
}
