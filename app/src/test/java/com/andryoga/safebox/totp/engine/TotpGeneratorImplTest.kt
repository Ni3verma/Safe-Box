package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TotpGeneratorImplTest {

    private lateinit var totpGenerator: TotpGeneratorImpl

    // Standard RFC 6238 20-byte seed: "12345678901234567890" in Base32
    private val rfc6238Base32SecretSha1 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    // 32-byte seed for SHA256: "12345678901234567890123456789012" in Base32
    private val rfc6238Base32SecretSha256 =
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA===="

    // 64-byte seed for SHA512: "1234567890123456789012345678901234567890123456789012345678901234" in Base32 (60 bytes + 4 bytes)
    private val rfc6238Base32SecretSha512 =
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA="

    @Before
    fun setUp() {
        totpGenerator = TotpGeneratorImpl()
    }

    @Test
    fun generateCode_withRfc6238Sha1TestVectors8Digits_matchesOfficialSpecification() {
        // Official RFC 6238 Appendix B test vectors for 8 digits
        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 59L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("94287082")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 1111111109L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("07081804")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 1111111111L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("14050471")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 1234567890L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("89005924")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 2000000000L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("69279037")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 20000000000L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("65353130")
    }

    @Test
    fun generateCode_withRfc6238Sha1TestVectors6Digits_matchesExpectedTruncation() {
        // Standard 6-digit truncation (last 6 digits of the 8-digit RFC vectors)
        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 59L,
                digits = 6,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("287082")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 1111111109L,
                digits = 6,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("081804")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 1234567890L,
                digits = 6,
                algorithm = TotpAlgorithm.SHA1
            )
        ).isEqualTo("005924")
    }

    @Test
    fun generateCode_withSha256Algorithm_generatesAccurateCode() {
        // Official RFC 6238 Table 1 test vectors for SHA256
        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha256,
                timeSeconds = 59L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA256
            )
        ).isEqualTo("46119246")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha256,
                timeSeconds = 1111111109L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA256
            )
        ).isEqualTo("68084774")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha256,
                timeSeconds = 1234567890L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA256
            )
        ).isEqualTo("91819424")
    }

    @Test
    fun generateCode_withSha512Algorithm_generatesAccurateCode() {
        // Official RFC 6238 Table 1 test vectors for SHA512
        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha512,
                timeSeconds = 59L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA512
            )
        ).isEqualTo("90693936")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha512,
                timeSeconds = 1111111109L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA512
            )
        ).isEqualTo("25091201")

        assertThat(
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha512,
                timeSeconds = 1234567890L,
                digits = 8,
                algorithm = TotpAlgorithm.SHA512
            )
        ).isEqualTo("93441116")
    }

    @Test
    fun getRemainingSeconds_computesAccurateCountdown() {
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = 30L, period = 30)).isEqualTo(30)
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = 31L, period = 30)).isEqualTo(29)
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = 59L, period = 30)).isEqualTo(1)
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = 60L, period = 30)).isEqualTo(30)
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = 45L, period = 60)).isEqualTo(15)
    }

    @Test
    fun generateCode_withInvalidPeriodOrDigits_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 100L,
                period = 0
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 100L,
                digits = 5
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                secretBase32 = rfc6238Base32SecretSha1,
                timeSeconds = 100L,
                digits = 9
            )
        }
    }

    @Test
    fun isValidSecret_validatesCorrectly() {
        assertThat(totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP")).isTrue()
        assertThat(totpGenerator.isValidSecret("invalid_secret_1234!")).isFalse()
    }
}
