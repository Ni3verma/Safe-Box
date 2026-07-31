package com.andryoga.safebox.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class SecurityUtilsTest {

    @get:Rule
    val base64Rule = Base64Rule()

    @Test
    fun encodeBase64_withByteArray_returnsValidBase64String() {
        val input = "SafeBoxTestInput".toByteArray(Charsets.UTF_8)

        val encoded = SecurityUtils.encodeBase64(input)

        assertThat(encoded).isNotEmpty()
        assertThat(encoded.trim()).matches("^[A-Za-z0-9+/=]+$")
    }

    @Test
    fun decodeBase64_withBase64String_returnsOriginalByteArray() {
        val original = "DecodableContent123".toByteArray(Charsets.UTF_8)
        val encoded = SecurityUtils.encodeBase64(original)

        val decoded = SecurityUtils.decodeBase64(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun encodeBase64AndDecodeBase64_roundtripWithArbitraryBytes_preservesContent() {
        val randomBytes = CryptoTestFixtures.generateRandomBytes(128)

        val encoded = SecurityUtils.encodeBase64(randomBytes)
        val decoded = SecurityUtils.decodeBase64(encoded)

        assertThat(decoded).isEqualTo(randomBytes)
    }

    @Test
    fun encodeBase64AndDecodeBase64_roundtripWithEmptyByteArray_preservesEmptyByteArray() {
        val emptyBytes = ByteArray(0)

        val encoded = SecurityUtils.encodeBase64(emptyBytes)
        val decoded = SecurityUtils.decodeBase64(encoded)

        assertThat(decoded).isEqualTo(emptyBytes)
    }

    @Test
    fun encodeBase64AndDecodeBase64_roundtripWithLargeByteArray_preservesContent() {
        val largeBytes = ByteArray(32_768) { (it % 256).toByte() }

        val encoded = SecurityUtils.encodeBase64(largeBytes)
        val decoded = SecurityUtils.decodeBase64(encoded)

        assertThat(decoded).isEqualTo(largeBytes)
    }

    @Test
    fun decodeBase64_withStringContainingWhitespaceOrNewlines_decodesCorrectly() {
        val originalText = "NewlineWhitespaceTest"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val encoded = SecurityUtils.encodeBase64(originalBytes)
        val formattedWithWhitespace = "  " + encoded.trim() + " \n \r\n "

        val decoded = SecurityUtils.decodeBase64(formattedWithWhitespace)

        assertThat(decoded).isEqualTo(originalBytes)
    }

    @Test
    fun decodeBase64_withInvalidBase64Characters_throwsIllegalArgumentException() {
        val invalidBase64 = "###Invalid_Base64_String###"

        assertThrows(IllegalArgumentException::class.java) {
            SecurityUtils.decodeBase64(invalidBase64)
        }
    }
}
