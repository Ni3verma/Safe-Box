package com.andryoga.safebox.security.envelope

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Result of [WrapKeyOpener.probe]: whether a Keystore wrap key can be used right now.
 */
sealed interface WrapKeyProbe {
    /** The private key initialised a cipher; bind it to the prompt as the `CryptoObject`. */
    class Ready(val cipher: Cipher) : WrapKeyProbe

    /**
     * The key is healthy but refuses to initialise until the user authenticates (a time-bound key
     * outside its validity window). Prompt without a `CryptoObject` and call [WrapKeyOpener.open]
     * right after the prompt succeeds.
     */
    data object AwaitingAuthentication : WrapKeyProbe

    /** The key is gone, unrecoverable or invalidated by an enrolment change; its wrap is useless. */
    data object Invalidated : WrapKeyProbe
}

/**
 * Result of [WrapKeyOpener.open]: what unwrapping a Keystore-wrapped DEK produced.
 */
sealed interface WrapOpenOutcome {
    /** The wrap held this vault's DEK (it passed `dek_verifier`); the caller owns and zeroes it. */
    class Opened(val dek: ByteArray) : WrapOpenOutcome

    /** A time-bound key refused the unwrap because the authentication window had already closed. */
    data object NotAuthenticated : WrapOpenOutcome

    /** The key worked but the bytes are not this vault's DEK (`dek_verifier` mismatch). */
    data object WrongDek : WrapOpenOutcome

    /** The key could not unwrap: after a successful prompt this means the key is invalidated. */
    class KeystoreFailure(val cause: Exception) : WrapOpenOutcome
}

/**
 * Opens a Keystore-wrapped DEK (`wrapped_dek_biometric` or `wrapped_dek_device`) in two steps
 * shared by biometric login and forgot-password recovery: [probe] before the prompt, [open]
 * after it. Both run blocking crypto and belong on `dispatchers.default`.
 */
class WrapKeyOpener @Inject constructor(
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
) {
    /**
     * Initialises the decrypt cipher of the wrap key for [spec]; the `Cipher.init` doubles as
     * the health probe. Every failure other than the authentication window maps to
     * [WrapKeyProbe.Invalidated]: a wrap is disposable and is recreated from the DEK in RAM.
     */
    fun probe(spec: WrapKeySpec, aliasSuffix: String?): WrapKeyProbe = try {
        WrapKeyProbe.Ready(envelopeEncryptionManager.decryptCipherFor(spec, aliasSuffix))
    } catch (_: UserNotAuthenticatedException) {
        WrapKeyProbe.AwaitingAuthentication
    } catch (_: KeyPermanentlyInvalidatedException) {
        WrapKeyProbe.Invalidated
    } catch (e: Exception) {
        flagIfOutsideCryptoApi("wrap key probe failed outside the crypto API", e)
        Timber.w(e, "%s wrap key unusable", spec.wireName)
        WrapKeyProbe.Invalidated
    }

    /**
     * Unwraps [blob] with [cipher] — the one the prompt authorised — or, when [cipher] is `null`,
     * with a cipher initialised now (only valid inside a time-bound key's window), then checks the
     * result against [verifier].
     *
     * @param cipher Authorised decrypt cipher, or `null` for the time-bound path.
     * @param spec Wrap key the blob was made with.
     * @param aliasSuffix Per-generation alias suffix of that key.
     * @param blob Base64 RSA-OAEP wrap of the DEK.
     * @param verifier Base64 `dek_verifier` blob of the vault.
     */
    fun open(
        cipher: Cipher?,
        spec: WrapKeySpec,
        aliasSuffix: String?,
        blob: String,
        verifier: String,
    ): WrapOpenOutcome {
        val dek = try {
            val authorisedCipher = cipher ?: envelopeEncryptionManager.decryptCipherFor(spec, aliasSuffix)
            envelopeEncryptionManager.unwrapWithCipher(authorisedCipher, blob)
        } catch (_: UserNotAuthenticatedException) {
            return WrapOpenOutcome.NotAuthenticated
        } catch (e: Exception) {
            flagIfOutsideCryptoApi("wrap unwrap failed outside the crypto contract", e)
            return WrapOpenOutcome.KeystoreFailure(e)
        }
        if (!envelopeEncryptionManager.verify(dek, verifier)) {
            dek.fill(0)
            return WrapOpenOutcome.WrongDek
        }
        return WrapOpenOutcome.Opened(dek)
    }

    /** Crypto-API failures vary by OEM and are expected; any other class is a programming error. */
    private fun flagIfOutsideCryptoApi(message: String, error: Exception) {
        if (error !is GeneralSecurityException && error !is ProviderException && error !is IllegalArgumentException) {
            Utils.crashInDebugBuild(message, error)
        }
    }
}
