package com.andryoga.safebox.security.envelope

import java.security.GeneralSecurityException
import java.security.MessageDigest

/**
 * Creates and checks the `dek_verifier` blob (`0x01 || iv(12) || AES-256-GCM(DEK, "safebox.dek.verifier.v1", aad = "verifier")`).
 *
 * Because RSA-OAEP public keys can be read without authentication, an RSA wrap cannot prove by
 * itself that it contains this vault's DEK. Every unwrap checks the candidate DEK against this
 * verifier before installing it in the session or overwriting another wrap.
 */
object DekVerifier {
    private val VERIFIER_PLAINTEXT = "safebox.dek.verifier.v1".toByteArray(Charsets.UTF_8)
    private val VERIFIER_AAD = "verifier".toByteArray(Charsets.UTF_8)

    /**
     * Encrypts the canonical verifier payload under [dek].
     *
     * @param dek 32-byte Data Encryption Key.
     * @return Base64-encoded `dek_verifier` blob.
     */
    fun makeVerifier(dek: ByteArray): String =
        AesGcmWrap.wrap(keyBytes = dek, plaintext = VERIFIER_PLAINTEXT, aad = VERIFIER_AAD)

    /**
     * Checks whether [dek] decrypts [verifierBlob] to the canonical verifier payload.
     *
     * @param dek Candidate 32-byte Data Encryption Key.
     * @param verifierBlob Base64-encoded `dek_verifier` blob from `vault_meta`.
     * @return `true` if the GCM tag and plaintext match, `false` on any cryptographic or format failure.
     */
    fun verify(dek: ByteArray, verifierBlob: String): Boolean = try {
        val decrypted = AesGcmWrap.unwrap(
            keyBytes = dek,
            blob = verifierBlob,
            aad = VERIFIER_AAD,
        )
        MessageDigest.isEqual(decrypted, VERIFIER_PLAINTEXT)
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
