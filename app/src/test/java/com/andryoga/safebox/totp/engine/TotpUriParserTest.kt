package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TotpUriParserTest {

    @Test
    fun parse_withStandardGoogleAuthenticatorUri_parsesAllFieldsCorrectly() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("Google - alex@gmail.com")
        assertThat(parsed.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parsed.algorithm).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(parsed.digits).isEqualTo(6)
        assertThat(parsed.period).isEqualTo(30)
    }

    @Test
    fun parse_withEncodedCharactersInLabelAndIssuer_decodesProperly() {
        val uri =
            "otpauth://totp/GitHub%20Corp%3Auser%40corp.com?secret=MZXW6YTB&issuer=GitHub%20Corp"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("GitHub Corp - user@corp.com")
        assertThat(parsed.secretKey).isEqualTo("MZXW6YTB")
    }

    @Test
    fun parse_withCustomAlgorithmDigitsAndPeriod_parsesParametersAccurately() {
        val uri =
            "otpauth://totp/AWS:admin?secret=JBSWY3DPEHPK3PXP&issuer=AWS&algorithm=SHA256&digits=8&period=60"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("AWS - admin")
        assertThat(parsed.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parsed.algorithm).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(parsed.digits).isEqualTo(8)
        assertThat(parsed.period).isEqualTo(60)
    }

    @Test
    fun parse_withSha512Algorithm_setsSha512Enum() {
        val uri = "otpauth://totp/SecuritySystem?secret=JBSWY3DPEHPK3PXP&algorithm=SHA512"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("SecuritySystem")
        assertThat(parsed.algorithm).isEqualTo(TotpAlgorithm.SHA512)
    }

    @Test
    fun parse_withDashesAndSpacesInSecret_sanitizesSecretKey() {
        val uri = "otpauth://totp/Service?secret=jbsw-y3dp ehpk-3pxp"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun parse_withInvalidScheme_throwsIllegalArgumentException() {
        val uri = "https://example.com/totp?secret=JBSWY3DPEHPK3PXP"

        assertThrows(IllegalArgumentException::class.java) {
            TotpUriParser.parse(uri)
        }
    }

    @Test
    fun parse_withUnsupportedOtpType_throwsIllegalArgumentException() {
        val uri = "otpauth://hotp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&counter=1"

        assertThrows(IllegalArgumentException::class.java) {
            TotpUriParser.parse(uri)
        }
    }

    @Test
    fun parse_withMissingSecretKey_throwsIllegalArgumentException() {
        val uri = "otpauth://totp/Google:alex@gmail.com?issuer=Google"

        assertThrows(IllegalArgumentException::class.java) {
            TotpUriParser.parse(uri)
        }
    }

    @Test
    fun parse_withInvalidBase32Secret_throwsIllegalArgumentExceptionWithoutLeakingSecret() {
        val secret = "InvalidSecret0189"
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=$secret"

        val exception = assertThrows(IllegalArgumentException::class.java) {
            TotpUriParser.parse(uri)
        }
        assertThat(exception.message).doesNotContain(secret)
    }

    @Test
    fun parse_withEmptyIssuerParam_fallsBackToPrefix() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer="
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("Google - alex@gmail.com")
    }

    @Test
    fun parse_withPlusLogInEmailAndLabel_preservesPlusCharacters() {
        val uri = "otpauth://totp/C%2B%2B:user%2Btag@gmail.com?secret=JBSWY3DPEHPK3PXP"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("C++ - user+tag@gmail.com")
    }

    @Test
    fun parse_withColonAndEmptyPrefixInLabel_fallsBackToAccount() {
        val uri = "otpauth://totp/:user@gmail.com?secret=JBSWY3DPEHPK3PXP"
        val parsed = TotpUriParser.parse(uri)

        assertThat(parsed.title).isEqualTo("user@gmail.com")
    }
}
