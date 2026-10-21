package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.security.interfaces.WrongPasswordException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [EnvelopeEncryptionManager].
 *
 * Wraps the DEK under password-derived AES-256-GCM keys and JCA-re-encoded RSA-2048-OAEP public
 * keys (`OAEPWithSHA-256AndMGF1Padding` with explicit `MGF1ParameterSpec.SHA1`).
 */
@Singleton
class EnvelopeEncryptionManagerImpl @Inject constructor(
    private val keystoreGateway: KeystoreGateway,
) : EnvelopeEncryptionManager {

    private val secureRandom = SecureRandom()

    override fun mintDek(): ByteArray {
        val dek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES)
        secureRandom.nextBytes(dek)
        return dek
    }

    override fun deriveKdfParams(): KdfParams = PasswordKdf.newParams()

    override fun wrapWithPassword(password: CharArray, params: KdfParams, dek: ByteArray): String {
        requireDekSize(dek)
        var kek: ByteArray? = null
        return try {
            kek = PasswordKdf.deriveKey(password, params)
            AesGcmWrap.wrap(keyBytes = kek, plaintext = dek, aad = PASSWORD_WRAP_AAD)
        } finally {
            password.fill('\u0000')
            kek?.fill(0)
        }
    }

    override fun makeVerifier(dek: ByteArray): String = DekVerifier.makeVerifier(dek)

    override fun unwrapWithPassword(
        password: CharArray,
        params: KdfParams,
        blob: String,
    ): Result<ByteArray> {
        val dek = try {
            unwrapWithDerivedKek(password, params, blob)
        } catch (e: AEADBadTagException) {
            return Result.failure(WrongPasswordException(e))
        } catch (e: Exception) {
            // A malformed blob or a cipher failure is the expected "cannot open" answer; anything else is a bug.
            if (e !is GeneralSecurityException && e !is IllegalArgumentException) {
                Utils.crashInDebugBuild("Password unwrap failed outside the crypto contract", e)
            }
            return Result.failure(e)
        }
        if (dek.size != AesGcmWrap.KEY_SIZE_BYTES) {
            // The tag just proved this blob was written by wrapWithPassword, which only ever wraps a full DEK.
            dek.fill(0)
            val error = IllegalStateException("Password wrap opened to ${dek.size} bytes, not a DEK")
            Utils.crashInDebugBuild("wrapped_dek_password holds a DEK of the wrong size", error)
            return Result.failure(error)
        }
        return Result.success(dek)
    }

    /** Derives `KEK_Password`, opens [blob] with it, and zeroes both the password and the KEK before returning. */
    private fun unwrapWithDerivedKek(password: CharArray, params: KdfParams, blob: String): ByteArray {
        var kek: ByteArray? = null
        try {
            kek = PasswordKdf.deriveKey(password, params)
            return AesGcmWrap.unwrap(keyBytes = kek, blob = blob, aad = PASSWORD_WRAP_AAD)
        } finally {
            password.fill('\u0000')
            kek?.fill(0)
        }
    }

    override fun decryptCipherFor(spec: WrapKeySpec): Cipher =
        keystoreGateway.decryptCipher(aliasFor(spec))

    override fun unwrapWithCipher(cipher: Cipher, blob: String): ByteArray {
        val raw = try {
            Base64.getDecoder().decode(blob)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Base64 in RSA wrap blob", e)
        }
        require(raw.size == 1 + RSA_CIPHERTEXT_SIZE_BYTES) {
            "Unexpected RSA wrap blob size: ${raw.size} bytes"
        }
        require(raw[0] == RSA_WRAP_FORMAT_VERSION) {
            "Unsupported RSA wrap version: ${raw[0]}"
        }
        val dek = cipher.doFinal(raw, 1, raw.size - 1)
        requireDekSize(dek)
        return dek
    }

    override fun verify(dek: ByteArray, verifier: String): Boolean =
        DekVerifier.verify(dek, verifier)

    override fun wrapWithKeystoreKey(spec: WrapKeySpec, dek: ByteArray): String {
        requireDekSize(dek)
        val alias = aliasFor(spec)
        val publicKey = when (keystoreGateway.probe(alias)) {
            KeyHealth.Healthy ->
                keystoreGateway.publicKey(alias) ?: keystoreGateway.generateWrapKeyPair(alias, spec)
            KeyHealth.Missing,
            KeyHealth.Invalidated,
            -> keystoreGateway.generateWrapKeyPair(alias, spec)
        }
        // Re-encode the public key so the default JCA provider performs the encryption without Keystore.
        val jcaPublicKey = asJcaPublicKey(publicKey)
        val cipher = Cipher.getInstance(AndroidKeystoreGateway.RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, jcaPublicKey, AndroidKeystoreGateway.OAEP_SPEC)
        val ciphertext = cipher.doFinal(dek)
        check(ciphertext.size == RSA_CIPHERTEXT_SIZE_BYTES) {
            "Expected $RSA_CIPHERTEXT_SIZE_BYTES-byte RSA-2048 ciphertext, got ${ciphertext.size}"
        }
        val output = ByteArray(1 + ciphertext.size)
        output[0] = RSA_WRAP_FORMAT_VERSION
        System.arraycopy(ciphertext, 0, output, 1, ciphertext.size)
        return Base64.getEncoder().encodeToString(output)
    }

    private fun asJcaPublicKey(publicKey: PublicKey): PublicKey =
        KeyFactory.getInstance(KEY_ALGORITHM_RSA).generatePublic(X509EncodedKeySpec(publicKey.encoded))

    private fun requireDekSize(dek: ByteArray) {
        require(dek.size == AesGcmWrap.KEY_SIZE_BYTES) {
            "DEK must be ${AesGcmWrap.KEY_SIZE_BYTES} bytes, got ${dek.size}"
        }
    }

    companion object {
        const val BIOMETRIC_WRAP_KEY_ALIAS = "safebox_kek_biometric"
        const val DEVICE_WRAP_KEY_ALIAS = "safebox_kek_device"

        /** First byte of an RSA wrap blob; a reader rejects any other value. */
        const val RSA_WRAP_FORMAT_VERSION: Byte = 0x01

        /** RSA-2048 always produces a 256-byte ciphertext, so the blob length is fixed. */
        const val RSA_CIPHERTEXT_SIZE_BYTES = 256
        private const val KEY_ALGORITHM_RSA = "RSA"
        private val PASSWORD_WRAP_AAD = "safebox.wrap.password.v1".toByteArray(Charsets.UTF_8)

        /**
         * Returns the Keystore alias corresponding to [spec].
         */
        // TODO(MR5): resolve a per-generation alias suffix from vault_meta so a stuck entry (API 30, ADR-0011 S2) gets a fresh alias (design §2.2, debt D13).
        fun aliasFor(spec: WrapKeySpec): String = when (spec) {
            WrapKeySpec.BIOMETRIC -> BIOMETRIC_WRAP_KEY_ALIAS
            WrapKeySpec.DEVICE -> DEVICE_WRAP_KEY_ALIAS
        }
    }
}
