package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.security.interfaces.WrongPasswordException
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
        val dek = ByteArray(SessionKeyHolderImpl.DEK_SIZE_BYTES)
        secureRandom.nextBytes(dek)
        return dek
    }

    override fun deriveKdfParams(): KdfParams = PasswordKdf.newParams()

    override fun wrapWithPassword(password: CharArray, params: KdfParams, dek: ByteArray): String {
        require(dek.size == SessionKeyHolderImpl.DEK_SIZE_BYTES) {
            "DEK must be ${SessionKeyHolderImpl.DEK_SIZE_BYTES} bytes, got ${dek.size}"
        }
        var kek: ByteArray? = null
        return try {
            kek = PasswordKdf.deriveKey(password, params)
            AesGcmWrap.wrap(
                keyBytes = kek,
                plaintext = dek,
                aad = PASSWORD_WRAP_AAD,
            )
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
        var kek: ByteArray? = null
        return try {
            kek = PasswordKdf.deriveKey(password, params)
            val dek = AesGcmWrap.unwrap(
                keyBytes = kek,
                blob = blob,
                aad = PASSWORD_WRAP_AAD,
            )
            if (dek.size != SessionKeyHolderImpl.DEK_SIZE_BYTES) {
                dek.fill(0)
                Result.failure(
                    IllegalArgumentException("Unwrapped DEK must be ${SessionKeyHolderImpl.DEK_SIZE_BYTES} bytes"),
                )
            } else {
                Result.success(dek)
            }
        } catch (e: AEADBadTagException) {
            Result.failure(WrongPasswordException(e))
        } catch (e: Exception) {
            Result.failure(e)
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
        require(raw[0] == RSA_WRAP_VERSION_1) {
            "Unsupported RSA wrap version: ${raw[0]}"
        }
        val dek = cipher.doFinal(raw, 1, raw.size - 1)
        require(dek.size == SessionKeyHolderImpl.DEK_SIZE_BYTES) {
            "Unwrapped DEK must be ${SessionKeyHolderImpl.DEK_SIZE_BYTES} bytes, got ${dek.size}"
        }
        return dek
    }

    override fun verify(dek: ByteArray, verifier: String): Boolean =
        DekVerifier.verify(dek, verifier)

    override fun wrapWithKeystoreKey(spec: WrapKeySpec, dek: ByteArray): String {
        require(dek.size == SessionKeyHolderImpl.DEK_SIZE_BYTES) {
            "DEK must be ${SessionKeyHolderImpl.DEK_SIZE_BYTES} bytes, got ${dek.size}"
        }
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
        require(ciphertext.size == RSA_CIPHERTEXT_SIZE_BYTES) {
            "Expected $RSA_CIPHERTEXT_SIZE_BYTES-byte RSA-2048 ciphertext, got ${ciphertext.size}"
        }
        val output = ByteArray(1 + ciphertext.size)
        output[0] = RSA_WRAP_VERSION_1
        System.arraycopy(ciphertext, 0, output, 1, ciphertext.size)
        return Base64.getEncoder().encodeToString(output)
    }

    private fun asJcaPublicKey(publicKey: PublicKey): PublicKey =
        KeyFactory.getInstance(KEY_ALGORITHM_RSA).generatePublic(X509EncodedKeySpec(publicKey.encoded))

    companion object {
        const val BIOMETRIC_WRAP_KEY_ALIAS = "safebox_kek_biometric"
        const val DEVICE_WRAP_KEY_ALIAS = "safebox_kek_device"
        const val RSA_WRAP_VERSION_1: Byte = 0x01
        const val RSA_CIPHERTEXT_SIZE_BYTES = 256
        private const val KEY_ALGORITHM_RSA = "RSA"
        private val PASSWORD_WRAP_AAD = "safebox.wrap.password.v1".toByteArray(Charsets.UTF_8)

        /**
         * Returns the Keystore alias corresponding to [spec].
         */
        fun aliasFor(spec: WrapKeySpec): String = when (spec) {
            WrapKeySpec.BIOMETRIC -> BIOMETRIC_WRAP_KEY_ALIAS
            WrapKeySpec.DEVICE -> DEVICE_WRAP_KEY_ALIAS
        }
    }
}
