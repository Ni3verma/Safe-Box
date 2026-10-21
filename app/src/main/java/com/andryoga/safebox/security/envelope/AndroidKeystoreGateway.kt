package com.andryoga.safebox.security.envelope

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import androidx.annotation.VisibleForTesting
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.UnrecoverableKeyException
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [KeystoreGateway] backed by `AndroidKeyStore`.
 *
 * Generates RSA-2048 OAEP wrapping key pairs with StrongBox-first fallback, initializes private
 * decryption ciphers with explicit MGF1-SHA1 parameters for Conscrypt/Keystore interoperability,
 * and never regenerates the legacy `"symmetricDataKey"` entry.
 */
@Singleton
class AndroidKeystoreGateway @VisibleForTesting internal constructor(
    private val keyStoreProvider: () -> KeyStore,
    private val keyPairGeneratorProvider: () -> KeyPairGenerator,
    private val cipherProvider: () -> Cipher,
    private val specFactory: (alias: String, spec: WrapKeySpec, strongBoxBacked: Boolean) -> AlgorithmParameterSpec,
) : KeystoreGateway {

    @Inject
    constructor() : this(
        keyStoreProvider = {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        },
        keyPairGeneratorProvider = {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        },
        cipherProvider = {
            Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        },
        specFactory = { alias, spec, strongBoxBacked ->
            buildKeyGenParameterSpec(alias, spec, strongBoxBacked)
        },
    )

    override fun generateWrapKeyPair(alias: String, spec: WrapKeySpec): PublicKey {
        deleteKey(alias)
        return try {
            try {
                generateWithSpec(alias, spec, strongBoxBacked = true)
            } catch (e: Exception) {
                if (e.isStrongBoxUnavailable()) {
                    generateWithSpec(alias, spec, strongBoxBacked = false)
                } else {
                    throw e
                }
            }
        } catch (e: InvalidAlgorithmParameterException) {
            throw mapKeyGenFailure(e, spec)
        } catch (e: IllegalStateException) {
            throw mapKeyGenFailure(e, spec)
        } catch (e: ProviderException) {
            throw mapKeyGenFailure(e, spec)
        }
    }

    override fun publicKey(alias: String): PublicKey? = try {
        keyStoreProvider().getCertificate(alias)?.publicKey
    } catch (e: GeneralSecurityException) {
        Timber.w(e, "Failed to read public key for alias")
        null
    } catch (e: ProviderException) {
        Timber.w(e, "Failed to read public key for alias")
        null
    }

    override fun decryptCipher(alias: String): Cipher {
        val keyStore = keyStoreProvider()
        val privateKey = try {
            keyStore.getKey(alias, null) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            throw KeyPermanentlyInvalidatedException("Key unrecoverable for alias $alias", e)
        } catch (e: KeyStoreException) {
            throw KeyPermanentlyInvalidatedException("KeyStore failure for alias $alias", e)
        } ?: throw KeyPermanentlyInvalidatedException("Private key missing for alias $alias")

        val cipher = cipherProvider()
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        return cipher
    }

    override fun probe(alias: String): KeyHealth {
        val keyStore = try {
            keyStoreProvider()
        } catch (_: Exception) {
            return KeyHealth.Invalidated
        }
        val privateKey = try {
            keyStore.getKey(alias, null) as? PrivateKey
        } catch (_: UnrecoverableKeyException) {
            return KeyHealth.Invalidated
        } catch (_: KeyStoreException) {
            return KeyHealth.Invalidated
        } catch (_: ProviderException) {
            return KeyHealth.Invalidated
        }
        if (privateKey == null) {
            val aliasPresent = runCatching { keyStore.containsAlias(alias) }.getOrDefault(false)
            return if (aliasPresent) KeyHealth.Invalidated else KeyHealth.Missing
        }
        return try {
            cipherProvider().init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
            KeyHealth.Healthy
        } catch (_: UserNotAuthenticatedException) {
            // Time-bound keys on API 29 throw at init when outside the validity window, which is healthy.
            KeyHealth.Healthy
        } catch (_: KeyPermanentlyInvalidatedException) {
            KeyHealth.Invalidated
        } catch (_: GeneralSecurityException) {
            KeyHealth.Invalidated
        } catch (_: ProviderException) {
            KeyHealth.Invalidated
        }
    }

    override fun deleteKey(alias: String) {
        try {
            val keyStore = keyStoreProvider()
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: KeyStoreException) {
            // API 30 can fail to delete an entry after lock-screen removal; treated as non-fatal.
            Timber.w(e, "Failed to delete keystore entry")
        } catch (e: ProviderException) {
            Timber.w(e, "Failed to delete keystore entry")
        }
    }

    override fun legacyVaultKey(): SecretKey? = try {
        keyStoreProvider().getKey(LEGACY_SYMMETRIC_KEY_ALIAS, null) as? SecretKey
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: ProviderException) {
        null
    }

    override fun deleteLegacyVaultKey() {
        deleteKey(LEGACY_SYMMETRIC_KEY_ALIAS)
    }

    private fun generateWithSpec(
        alias: String,
        spec: WrapKeySpec,
        strongBoxBacked: Boolean,
    ): PublicKey {
        val generator = keyPairGeneratorProvider()
        generator.initialize(specFactory(alias, spec, strongBoxBacked))
        return generator.generateKeyPair().public
    }

    private fun Throwable.isStrongBoxUnavailable(): Boolean =
        this is StrongBoxUnavailableException || cause is StrongBoxUnavailableException

    private fun mapKeyGenFailure(error: Exception, spec: WrapKeySpec): Exception {
        val illegalState = error as? IllegalStateException ?: error.cause as? IllegalStateException
        if (illegalState != null) {
            val message = illegalState.message.orEmpty()
            return when {
                message.contains("biometric", ignoreCase = true) ->
                    BiometricNotEnrolledException(message.ifEmpty { "Biometric not enrolled" }, error)
                message.contains("lock screen", ignoreCase = true) ->
                    LockScreenRequiredException(message.ifEmpty { "Lock screen required" }, error)
                spec == WrapKeySpec.BIOMETRIC ->
                    BiometricNotEnrolledException(message.ifEmpty { "Biometric not enrolled" }, error)
                else ->
                    LockScreenRequiredException(message.ifEmpty { "Lock screen required" }, error)
            }
        }
        return error
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val LEGACY_SYMMETRIC_KEY_ALIAS = "symmetricDataKey"
        const val RSA_KEY_SIZE_BITS = 2048
        const val DEVICE_KEY_VALIDITY_SECONDS = 5
        const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        val OAEP_SPEC: OAEPParameterSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT,
        )

        /**
         * Builds the `KeyGenParameterSpec` for [spec] according to design §2.2.
         */
        @VisibleForTesting
        internal fun buildKeyGenParameterSpec(
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
    }
}
