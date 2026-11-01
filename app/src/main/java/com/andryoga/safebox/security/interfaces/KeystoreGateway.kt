package com.andryoga.safebox.security.interfaces

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import com.andryoga.safebox.data.db.models.VaultMetaKey
import java.security.GeneralSecurityException
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Identifies which hardware-backed RSA-2048 wrapping key pair is targeted.
 *
 * @property wrappedDekKey `vault_meta` key storing the wrapped DEK blob for this key pair.
 * @property aliasSuffixKey `vault_meta` key storing the per-generation Keystore alias suffix.
 * @property wireName Analytics wire value identifying this wrap type.
 */
enum class WrapKeySpec(
    val wrappedDekKey: String,
    val aliasSuffixKey: String,
    val wireName: String,
) {
    BIOMETRIC(
        wrappedDekKey = VaultMetaKey.WRAPPED_DEK_BIOMETRIC,
        aliasSuffixKey = VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX,
        wireName = "biometric",
    ),
    DEVICE(
        wrappedDekKey = VaultMetaKey.WRAPPED_DEK_DEVICE,
        aliasSuffixKey = VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX,
        wireName = "device",
    ),
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
     * @throws KeyPermanentlyInvalidatedException When the key is gone, unrecoverable or
     * invalidated by an enrolment change.
     * @throws UserNotAuthenticatedException When a time-bound key is outside its validity
     * window (API 29).
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
    // TODO(D3): legacy vault (crypto_version = 1) only — the handle its fields and hint are under
    fun legacyVaultKey(): SecretKey?

    /**
     * Deletes the legacy `"symmetricDataKey"` entry if present.
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    fun deleteLegacyVaultKey()
}
