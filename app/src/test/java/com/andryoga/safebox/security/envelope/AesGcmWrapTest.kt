package com.andryoga.safebox.security.envelope

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException

class AesGcmWrapTest {

    private val kek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }
    private val wrongKek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 2).toByte() }
    private val dek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (0xA0 + index).toByte() }
    private val aad = "safebox.wrap.password.v1".toByteArray(Charsets.UTF_8)

    @Test
    fun wrapAndUnwrap_withMatchingKeyAndAad_shouldRoundTripPlaintext() {
        val blob = AesGcmWrap.wrap(keyBytes = kek, plaintext = dek, aad = aad)
        val unwrapped = AesGcmWrap.unwrap(keyBytes = kek, blob = blob, aad = aad)

        assertThat(unwrapped).isEqualTo(dek)
    }

    @Test
    fun unwrap_withWrongKek_shouldThrowAeadBadTagException() {
        val blob = AesGcmWrap.wrap(keyBytes = kek, plaintext = dek, aad = aad)

        assertThrows(AEADBadTagException::class.java) {
            AesGcmWrap.unwrap(keyBytes = wrongKek, blob = blob, aad = aad)
        }
    }

    @Test
    fun unwrap_withMismatchedAad_shouldThrowAeadBadTagException() {
        val blob = AesGcmWrap.wrap(keyBytes = kek, plaintext = dek, aad = aad)
        val wrongAad = "safebox.wrap.password.v2".toByteArray(Charsets.UTF_8)

        assertThrows(AEADBadTagException::class.java) {
            AesGcmWrap.unwrap(keyBytes = kek, blob = blob, aad = wrongAad)
        }
    }

    @Test
    fun unwrap_withTruncatedOrUnknownVersionBlob_shouldThrowIllegalArgumentException() {
        val truncatedBlob = Base64.getEncoder().encodeToString(ByteArray(10) { 1 })
        val unknownVersionBytes = ByteArray(40).apply { this[0] = 0x02 }
        val unknownVersionBlob = Base64.getEncoder().encodeToString(unknownVersionBytes)

        assertThrows(IllegalArgumentException::class.java) {
            AesGcmWrap.unwrap(keyBytes = kek, blob = truncatedBlob, aad = aad)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmWrap.unwrap(keyBytes = kek, blob = unknownVersionBlob, aad = aad)
        }
    }
}
