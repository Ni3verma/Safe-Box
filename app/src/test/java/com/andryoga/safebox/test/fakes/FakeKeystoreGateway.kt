package com.andryoga.safebox.test.fakes

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.AndroidKeystoreGateway
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * In-memory JCE RSA-2048 [KeystoreGateway] fake for JVM unit tests.
 *
 * Uses real JCE RSA key pairs and explicit `MGF1ParameterSpec.SHA1` OAEP parameters so the full
 * wrap/unwrap cryptographic path runs on the JVM while authentication and invalidation states
 * remain deterministically controllable.
 */
class FakeKeystoreGateway : KeystoreGateway {
    var isLockScreenConfigured: Boolean = true
    var isBiometricEnrolled: Boolean = true
    // TODO(D3): legacy vault (crypto_version = 1) only — the in-memory stand-in for the legacy alias
    var legacyKey: SecretKey? = null

    private val keyPairs = mutableMapOf<String, KeyPair>()
    private val keySpecs = mutableMapOf<String, WrapKeySpec>()
    private val generationCounts = mutableMapOf<String, Int>()
    private val invalidatedAliases = mutableSetOf<String>()
    private val unauthenticatedAliases = mutableSetOf<String>()

    override fun generateWrapKeyPair(alias: String, spec: WrapKeySpec): PublicKey {
        if (!isLockScreenConfigured) {
            throw LockScreenRequiredException()
        }
        if (spec == WrapKeySpec.BIOMETRIC && !isBiometricEnrolled) {
            throw BiometricNotEnrolledException()
        }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(AndroidKeystoreGateway.RSA_KEY_SIZE_BITS)
        val pair = generator.generateKeyPair()
        keyPairs[alias] = pair
        keySpecs[alias] = spec
        generationCounts[alias] = (generationCounts[alias] ?: 0) + 1
        invalidatedAliases.remove(alias)
        unauthenticatedAliases.remove(alias)
        return pair.public
    }

    override fun publicKey(alias: String): PublicKey? = keyPairs[alias]?.public

    override fun decryptCipher(alias: String): Cipher {
        if (alias in invalidatedAliases) {
            throw KeyPermanentlyInvalidatedException("Key $alias is permanently invalidated")
        }
        if (alias in unauthenticatedAliases) {
            throw UserNotAuthenticatedException("Key $alias requires user authentication")
        }
        val pair = keyPairs[alias]
            ?: throw KeyPermanentlyInvalidatedException("Key $alias does not exist")
        val cipher = Cipher.getInstance(AndroidKeystoreGateway.RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, pair.private, AndroidKeystoreGateway.OAEP_SPEC)
        return cipher
    }

    override fun probe(alias: String): KeyHealth = when {
        alias in invalidatedAliases -> KeyHealth.Invalidated
        alias !in keyPairs -> KeyHealth.Missing
        else -> KeyHealth.Healthy
    }

    override fun deleteKey(alias: String) {
        keyPairs.remove(alias)
        keySpecs.remove(alias)
        invalidatedAliases.remove(alias)
        unauthenticatedAliases.remove(alias)
    }

    override fun legacyVaultKey(): SecretKey? = legacyKey

    fun getOrCreateLegacyVaultKey(): SecretKey {
        return legacyKey ?: KeyGenerator.getInstance("AES").apply { init(AES_KEY_SIZE_BITS) }
            .generateKey()
            .also { legacyKey = it }
    }

    override fun deleteLegacyVaultKey() {
        legacyKey = null
    }

    /**
     * Marks [alias] as permanently invalidated so subsequent [probe] and [decryptCipher] calls
     * simulate a biometric enrollment change or lock-screen removal.
     */
    fun invalidateKey(alias: String) {
        invalidatedAliases.add(alias)
    }

    /**
     * Configures whether [alias] throws [UserNotAuthenticatedException] at [decryptCipher] while
     * still reporting [KeyHealth.Healthy] from [probe].
     */
    fun setRequireAuthOnInit(alias: String, required: Boolean = true) {
        if (required) {
            unauthenticatedAliases.add(alias)
        } else {
            unauthenticatedAliases.remove(alias)
        }
    }

    /**
     * Returns how many times [generateWrapKeyPair] succeeded for [alias].
     */
    fun generationCountFor(alias: String): Int = generationCounts[alias] ?: 0

    /**
     * Clears all stored keys, counters, and simulated failure states.
     */
    fun reset() {
        isLockScreenConfigured = true
        isBiometricEnrolled = true
        legacyKey = null
        keyPairs.clear()
        keySpecs.clear()
        generationCounts.clear()
        invalidatedAliases.clear()
        unauthenticatedAliases.clear()
    }

    private companion object {
        const val AES_KEY_SIZE_BITS = AesGcmWrap.KEY_SIZE_BYTES * 8
    }
}
