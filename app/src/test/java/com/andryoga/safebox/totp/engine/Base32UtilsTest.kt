package com.andryoga.safebox.totp.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class Base32UtilsTest {

    @Test
    fun decode_withRfc4648TestVectors_decodesAccurately() {
        // RFC 4648 Base32 test vectors
        // "" -> ""
        // "MY======" -> "f"
        // "MZXQ====" -> "fo"
        // "MZXW6===" -> "foo"
        // "MZXW6YQ=" -> "foob"
        // "MZXW6YTB" -> "fooba"
        // "MZXW6YTBOI======" -> "foobar"
        assertThat(String(Base32Utils.decode(""))).isEqualTo("")
        assertThat(String(Base32Utils.decode("MY======"))).isEqualTo("f")
        assertThat(String(Base32Utils.decode("MZXQ===="))).isEqualTo("fo")
        assertThat(String(Base32Utils.decode("MZXW6==="))).isEqualTo("foo")
        assertThat(String(Base32Utils.decode("MZXW6YQ="))).isEqualTo("foob")
        assertThat(String(Base32Utils.decode("MZXW6YTB"))).isEqualTo("fooba")
        assertThat(String(Base32Utils.decode("MZXW6YTBOI======"))).isEqualTo("foobar")
    }

    @Test
    fun decode_withLowercaseAndWhitespaceAndDashes_decodesSuccessfully() {
        val rawSecret = "mzxw 6ytb oi======"
        val decoded = Base32Utils.decode(rawSecret)

        assertThat(String(decoded)).isEqualTo("foobar")

        val dashedSecret = "MZXW-6YTB-OI======"
        val dashedDecoded = Base32Utils.decode(dashedSecret)

        assertThat(String(dashedDecoded)).isEqualTo("foobar")
    }

    @Test
    fun decode_withStandardTotpSecretKey_decodesCorrectByteArray() {
        val secret = "JBSWY3DPEHPK3PXP" // Standard test seed
        val decoded = Base32Utils.decode(secret)

        assertThat(decoded).isNotEmpty()
        assertThat(decoded.size).isEqualTo(10)
    }

    @Test
    fun decode_withInvalidBase32Character_throwsIllegalArgumentException() {
        val invalidSecret = "MZXW6890" // '8', '9', '0' are not valid RFC 4648 Base32

        assertThrows(IllegalArgumentException::class.java) {
            Base32Utils.decode(invalidSecret)
        }
    }

    @Test
    fun isValidBase32_withValidStrings_returnsTrue() {
        assertThat(Base32Utils.isValidBase32("JBSWY3DPEHPK3PXP")).isTrue()
        assertThat(Base32Utils.isValidBase32("jbswy3dpehpk3pxp")).isTrue()
        assertThat(Base32Utils.isValidBase32("JBSW Y3DP-EHPK3PXP=")).isTrue()
        assertThat(Base32Utils.isValidBase32("MZXW6YTB")).isTrue()
    }

    @Test
    fun isValidBase32_withInvalidStrings_returnsFalse() {
        assertThat(Base32Utils.isValidBase32("")).isFalse()
        assertThat(Base32Utils.isValidBase32("   ")).isFalse()
        assertThat(Base32Utils.isValidBase32("1890Invalid!")).isFalse()
        assertThat(Base32Utils.isValidBase32("JBSWY3DP8")).isFalse()
    }
}
