package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.totp.models.TotpUriParseResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TotpUriParserTest {

    @Test
    fun parse_withStandardGoogleAuthenticatorUri_parsesAllFieldsCorrectly() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("Google - alex@gmail.com")
        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(parsed.config.digits).isEqualTo(6)
        assertThat(parsed.config.period).isEqualTo(30)
    }

    @Test
    fun parse_withEncodedCharactersInLabelAndIssuer_decodesProperly() {
        val uri =
            "otpauth://totp/GitHub%20Corp%3Auser%40corp.com?secret=MZXW6YTB&issuer=GitHub%20Corp"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("GitHub Corp - user@corp.com")
        assertThat(parsed.config.secretKey).isEqualTo("MZXW6YTB")
    }

    @Test
    fun parse_withCustomAlgorithmDigitsAndPeriod_parsesParametersAccurately() {
        val uri =
            "otpauth://totp/AWS:admin?secret=JBSWY3DPEHPK3PXP&issuer=AWS&algorithm=SHA256&digits=8&period=60"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("AWS - admin")
        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(parsed.config.digits).isEqualTo(8)
        assertThat(parsed.config.period).isEqualTo(60)
    }

    @Test
    fun parse_withSha512Algorithm_setsSha512Enum() {
        val uri = "otpauth://totp/SecuritySystem?secret=JBSWY3DPEHPK3PXP&algorithm=SHA512"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("SecuritySystem")
        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA512)
    }

    @Test
    fun parse_withLowerCaseAlgorithm_matchesCaseInsensitively() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&algorithm=sha256"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA256)
    }

    @Test
    fun parse_withBlankOptionalParams_fallsBackToDefaults() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&algorithm=&digits=&period="
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(parsed.config.digits).isEqualTo(6)
        assertThat(parsed.config.period).isEqualTo(30)
    }

    @Test
    fun parse_withDashesAndSpacesInSecret_sanitizesSecretKey() {
        val uri = "otpauth://totp/Service?secret=jbsw-y3dp ehpk-3pxp"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun parse_withPaddingAndWhitespaceInSecret_sanitizesPaddingAndWhitespaceInSecretKey() {
        val uri = "otpauth://totp/Service?secret=jbsw-y3dp%09ehpk-3pxp=="
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun parse_withInvalidScheme_returnsNotTotpUri() {
        val uri = "https://example.com/totp?secret=JBSWY3DPEHPK3PXP"

        assertThat(TotpUriParser.parse(uri)).isEqualTo(TotpUriParseResult.NotTotpUri)
    }

    @Test
    fun parse_withIllegalCharacterInLabel_returnsMalformedUri() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP|extra"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.MALFORMED_URI)
    }

    @Test
    fun parse_withCaretInLabel_returnsMalformedUri() {
        val uri = "otpauth://totp/ACME^Co:alex@acme.com?secret=JBSWY3DPEHPK3PXP"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.MALFORMED_URI)
    }

    @Test
    fun parse_withInvalidPercentEscapeInLabel_returnsMalformedUri() {
        val uri = "otpauth://totp/50%OffCo:alex@acme.com?secret=JBSWY3DPEHPK3PXP"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.MALFORMED_URI)
    }

    @Test
    fun parse_withMalformedUriOfAnotherScheme_returnsNotTotpUri() {
        val uri = "https://example.com/totp^path"

        assertThat(TotpUriParser.parse(uri)).isEqualTo(TotpUriParseResult.NotTotpUri)
    }

    @Test
    fun parse_withHotpType_returnsUnsupportedOtpType() {
        val uri = "otpauth://hotp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&counter=1"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_OTP_TYPE)
    }

    @Test
    fun parse_withMissingSecretKey_returnsInvalidSecret() {
        val uri = "otpauth://totp/Google:alex@gmail.com?issuer=Google"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.INVALID_SECRET)
    }

    @Test
    fun parse_withBlankSecretKey_returnsInvalidSecret() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=&issuer=Google"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.INVALID_SECRET)
    }

    @Test
    fun parse_withInvalidBase32Secret_returnsInvalidSecret() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=InvalidSecret0189"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.INVALID_SECRET)
    }

    @Test
    fun parse_withUnknownAlgorithm_returnsUnsupportedAlgorithmInsteadOfDefaulting() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&algorithm=SHA3"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_ALGORITHM)
    }

    @Test
    fun parse_withOutOfRangeDigits_returnsUnsupportedDigitsInsteadOfDefaulting() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&digits=9"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_DIGITS)
    }

    @Test
    fun parse_withNonNumericDigits_returnsUnsupportedDigits() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&digits=six"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_DIGITS)
    }

    @Test
    fun parse_withNonPositivePeriod_returnsUnsupportedPeriodInsteadOfDefaulting() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&period=0"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_PERIOD)
    }

    @Test
    fun parse_withNonNumericPeriod_returnsUnsupportedPeriod() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&period=thirty"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.UNSUPPORTED_PERIOD)
    }

    @Test
    fun parse_withEmptyIssuerParam_fallsBackToPrefix() {
        val uri = "otpauth://totp/Google:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer="
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("Google - alex@gmail.com")
    }

    @Test
    fun parse_withPlusLogInEmailAndLabel_preservesPlusCharacters() {
        val uri = "otpauth://totp/C%2B%2B:user%2Btag@gmail.com?secret=JBSWY3DPEHPK3PXP"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("C++ - user+tag@gmail.com")
    }

    @Test
    fun parse_withColonAndEmptyPrefixInLabel_fallsBackToAccount() {
        val uri = "otpauth://totp/:user@gmail.com?secret=JBSWY3DPEHPK3PXP"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("user@gmail.com")
    }

    @Test
    fun parse_withIssuerDifferentFromLabelPrefix_keepsBothNames() {
        // issuers that rebrand leave the old name in the label prefix, and dropping either one
        // would leave two accounts looking identical in the records list.
        val uri = "otpauth://totp/OldBrand:alex@gmail.com" +
            "?secret=JBSWY3DPEHPK3PXP&issuer=NewBrand"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("NewBrand (OldBrand) - alex@gmail.com")
    }

    @Test
    fun parse_withIssuerMatchingLabelPrefixInDifferentCase_doesNotRepeatTheName() {
        val uri = "otpauth://totp/GOOGLE:alex@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("Google - alex@gmail.com")
    }

    @Test
    fun parse_withExplicitSha1Algorithm_setsSha1Enum() {
        val uri = "otpauth://totp/Service?secret=JBSWY3DPEHPK3PXP&algorithm=SHA1"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.config.algorithm).isEqualTo(TotpAlgorithm.SHA1)
    }

    @Test
    fun parse_withColonAndBlankAccountInLabel_fallsBackToPrefix() {
        val uri = "otpauth://totp/GitHub:?secret=JBSWY3DPEHPK3PXP"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("GitHub")
    }

    @Test
    fun parse_withBlankLabelAndBlankIssuer_fallsBackToDefaultAuthenticatorAccountTitle() {
        val emptyPathUri = "otpauth://totp/?secret=JBSWY3DPEHPK3PXP"
        val emptyColonUri = "otpauth://totp/:?secret=JBSWY3DPEHPK3PXP"

        assertThat(parseSuccessfully(emptyPathUri).title).isEqualTo("Authenticator Account")
        assertThat(parseSuccessfully(emptyColonUri).title).isEqualTo("Authenticator Account")
    }

    @Test
    fun parse_withLiteralPlusInLabelAndIssuerQueryParam_preservesPlusCharacters() {
        val uri =
            "otpauth://totp/C++:dev+ops@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Google+Cloud"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("Google+Cloud (C++) - dev+ops@example.com")
    }

    @Test
    fun parse_withConflictingDuplicateSecret_returnsAmbiguousParametersInsteadOfPickingOne() {
        val uri = "otpauth://totp/Acme:dup@example.com?secret=JBSWY3DPEHPK3PXP" +
            "&secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        assertThat(parseUnsupportedReason(uri)).isEqualTo(TotpUriError.AMBIGUOUS_PARAMETERS)
    }

    @Test
    fun parse_withConflictingDuplicateGenerationParams_returnsAmbiguousParameters() {
        val digitsUri = "otpauth://totp/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP&digits=6&digits=8"
        val periodUri = "otpauth://totp/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP&period=30&period=60"
        val algorithmUri =
            "otpauth://totp/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP&algorithm=SHA1&algorithm=SHA256"

        assertThat(parseUnsupportedReason(digitsUri)).isEqualTo(TotpUriError.AMBIGUOUS_PARAMETERS)
        assertThat(parseUnsupportedReason(periodUri)).isEqualTo(TotpUriError.AMBIGUOUS_PARAMETERS)
        assertThat(parseUnsupportedReason(algorithmUri))
            .isEqualTo(TotpUriError.AMBIGUOUS_PARAMETERS)
    }

    @Test
    fun parse_withHarmlessDuplicateParams_stillParses() {
        // an identical repeat names one value, and a repeated issuer only affects the title, which
        // the user sees and can correct before saving. Neither is worth refusing the QR code over.
        val repeatedSecretUri =
            "otpauth://totp/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP&secret=JBSWY3DPEHPK3PXP"
        val repeatedIssuerUri =
            "otpauth://totp/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP&issuer=One&issuer=Two"

        assertThat(parseSuccessfully(repeatedSecretUri).config.secretKey)
            .isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parseSuccessfully(repeatedIssuerUri).config.secretKey)
            .isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun parse_withUppercaseSchemeAndType_parsesCaseInsensitively() {
        // RFC 3986 makes the scheme and authority case insensitive, and issuers do emit them
        // uppercase. Losing either ignoreCase flag would reject a perfectly valid QR code.
        val uri = "OTPAUTH://TOTP/AcmeCorp:alice@example.com?secret=JBSWY3DPEHPK3PXP"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("AcmeCorp - alice@example.com")
        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun parse_withOtpTypeThatMerelyStartsWithTotp_returnsUnsupportedOtpType() {
        // guards the exact-match check: a prefix comparison would accept these as time based.
        val suffixedUri = "otpauth://totp_random_99x/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP"
        val unrelatedUri = "otpauth://xyz_auth_123/Acme:a@example.com?secret=JBSWY3DPEHPK3PXP"

        assertThat(parseUnsupportedReason(suffixedUri))
            .isEqualTo(TotpUriError.UNSUPPORTED_OTP_TYPE)
        assertThat(parseUnsupportedReason(unrelatedUri))
            .isEqualTo(TotpUriError.UNSUPPORTED_OTP_TYPE)
    }

    @Test
    fun parse_withBlankLabelButIssuerParam_usesIssuerAsTitle() {
        val uri = "otpauth://totp/?secret=JBSWY3DPEHPK3PXP&issuer=AcmeCorp"

        assertThat(parseSuccessfully(uri).title).isEqualTo("AcmeCorp")
    }

    @Test
    fun parse_withUnknownExtraParams_ignoresThemAndKeepsTotpParams() {
        // the image value carries its own '=' and ':', so this also pins the limit = 2 split that
        // stops a value being chopped at its first '='.
        val uri = "otpauth://totp/Acme:extra@example.com?secret=JBSWY3DPEHPK3PXP" +
            "&issuer=Acme&random_flag=true&image=https://example.com/logo.png?v=2&foo=123"
        val parsed = parseSuccessfully(uri)

        assertThat(parsed.title).isEqualTo("Acme - extra@example.com")
        assertThat(parsed.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(parsed.config.digits).isEqualTo(TotpDefaults.DIGITS)
        assertThat(parsed.config.period).isEqualTo(TotpDefaults.PERIOD_SECONDS)
    }

    @Test
    fun parse_withSecretLongerThanHmacBlockSize_keepsEveryCharacter() {
        // 256 Base32 characters decode to a 160 byte key, well past the 64 byte SHA1 block size.
        // The matching generator test proves the key still produces the right code.
        val longSecret = "JBSWY3DPEHPK3PXP".repeat(16)
        val uri = "otpauth://totp/Acme:longkey@example.com?secret=$longSecret"

        assertThat(parseSuccessfully(uri).config.secretKey).isEqualTo(longSecret)
    }

    private fun parseSuccessfully(uri: String): ParsedTotpData {
        val result = TotpUriParser.parse(uri)

        assertThat(result).isInstanceOf(TotpUriParseResult.Success::class.java)
        return (result as TotpUriParseResult.Success).data
    }

    private fun parseUnsupportedReason(uri: String): TotpUriError {
        val result = TotpUriParser.parse(uri)

        assertThat(result).isInstanceOf(TotpUriParseResult.Unsupported::class.java)
        return (result as TotpUriParseResult.Unsupported).reason
    }
}
