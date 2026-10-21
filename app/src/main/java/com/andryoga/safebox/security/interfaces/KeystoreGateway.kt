package com.andryoga.safebox.security.interfaces

import java.security.GeneralSecurityException
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Identifies which hardware-backed RSA-2048 wrapping key pair is targeted.
 */
enum class WrapKeySpec {
    BIOMETRIC,
    DEVICE,
}

/**
 * Non-interactive health status of a Keystore wrapping key pair.
 */
sealed interface KeyHealth {
    data object Healthy : KeyHealth
    data object Missing : KeyHealth
    data object Invalidated : KeyHealth
}

/**
 * Thrown when generating an authentication-bound Keystore key fails because no secure lock screen
 * is configured on the device.
 */
class LockScreenRequiredException(
    message: String = "Secure lock screen is required to generate a wrapping key",
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

/**
 * Thrown when generating a biometric-bound Keystore key fails because no Class-3 biometric is
 * enrolled on the device.
 */
class BiometricNotEnrolledException(
    message: String = "At least one biometric must be enrolled to generate a biometric wrapping key",
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

/**
 * Seam over `AndroidKeyStore` for RSA-2048 OAEP wrapping key pairs and the legacy AES vault key.
 */
interface KeystoreGateway {
    /**
     * Generates a fresh RSA-2048 key pair for [spec] under [alias] and returns its public half.
     *
     * @param alias Keystore alias to create or replace.
     * @param spec Authentication binding policy for the private half.
     * @return The generated [PublicKey].
     * @throws LockScreenRequiredException If the device has no secure lock screen.
     * @throws BiometricNotEnrolledException If [spec] is [WrapKeySpec.BIOMETRIC] and no biometric is enrolled.
     */
    fun generateWrapKeyPair(alias: String, spec: WrapKeySpec): PublicKey

    /**
     * Returns the public half of [alias], or `null` if the entry is absent.
     */
    fun publicKey(alias: String): PublicKey?

    /**
     * Returns a `DECRYPT_MODE` RSA-OAEP [Cipher] initialized with the private half of [alias].
     *
     * @param alias Keystore alias whose private key initializes the cipher.
     * @return Initialized [Cipher] ready for `CryptoObject` binding or immediate unwrap.
     */
    fun decryptCipher(alias: String): Cipher

    /**
     * Probes the health of [alias] via `Cipher.init(DECRYPT_MODE)` without triggering a prompt.
     *
     * @param alias Keystore alias to inspect.
     * @return [KeyHealth] describing whether [alias] is usable, absent, or invalidated.
     */
    fun probe(alias: String): KeyHealth

    /**
     * Deletes [alias] from the Keystore if present, swallowing non-fatal Keystore deletion errors.
     */
    fun deleteKey(alias: String)

    /**
     * Returns the legacy `"symmetricDataKey"` handle if it exists, without ever regenerating it.
     */
    fun legacyVaultKey(): SecretKey?

    /**
     * Deletes the legacy `"symmetricDataKey"` entry if present.
     */
    fun deleteLegacyVaultKey()
}
