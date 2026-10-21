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
 * is configured on the device. The platform's own message travels as [cause].
 */
class LockScreenRequiredException(
    cause: Throwable? = null,
) : GeneralSecurityException("Secure lock screen is required to generate a wrapping key", cause)

/**
 * Thrown when generating a biometric-bound Keystore key fails because no Class-3 biometric is
 * enrolled on the device. The platform's own message travels as [cause].
 */
class BiometricNotEnrolledException(
    cause: Throwable? = null,
) : GeneralSecurityException("At least one biometric must be enrolled to generate a biometric wrapping key", cause)

/**
 * Seam over `AndroidKeyStore` for RSA-2048 OAEP wrapping key pairs and the legacy AES vault key.
 *
 * Two kinds of method, deliberately: the defensive ones ([probe], [deleteKey]) map every failure to
 * a safe answer; the operational ones translate only the failures they understand into the typed
 * exceptions below and let anything else propagate to the caller that owns the recovery.
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
     * Returns the public half of [alias], or `null` if the entry is absent. Keystore errors propagate.
     */
    fun publicKey(alias: String): PublicKey?

    /**
     * Returns a `DECRYPT_MODE` RSA-OAEP [Cipher] initialized with the private half of [alias].
     *
     * @param alias Keystore alias whose private key initializes the cipher.
     * @return Initialized [Cipher] ready for `CryptoObject` binding or immediate unwrap.
     * @throws android.security.keystore.KeyPermanentlyInvalidatedException When the key is gone,
     * unrecoverable or invalidated by an enrolment change.
     * @throws android.security.keystore.UserNotAuthenticatedException When a time-bound key is
     * outside its validity window (API 29).
     */
    fun decryptCipher(alias: String): Cipher

    /**
     * Probes the health of [alias] via `Cipher.init(DECRYPT_MODE)` without triggering a prompt.
     * Every Keystore failure maps to [KeyHealth.Invalidated]; recreating a wrap is always safe
     * because the DEK is in RAM when it happens.
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
     * Returns the legacy `"symmetricDataKey"` handle, or `null` only when the alias is absent, and
     * never regenerates it. Keystore errors propagate so a transient failure is retried instead
     * of being reported as a lost key.
     */
    fun legacyVaultKey(): SecretKey?

    /**
     * Deletes the legacy `"symmetricDataKey"` entry if present.
     */
    fun deleteLegacyVaultKey()
}
