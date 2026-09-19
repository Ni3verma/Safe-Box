package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
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

    private val sha1Config8Digits = TotpConfig(
        secretKey = rfc6238Base32SecretSha1,
        algorithm = TotpAlgorithm.SHA1,
        digits = 8,
    )
    private val sha1Config6Digits = sha1Config8Digits.copy(digits = 6)
    private val sha256Config8Digits = TotpConfig(
        secretKey = rfc6238Base32SecretSha256,
        algorithm = TotpAlgorithm.SHA256,
        digits = 8,
    )
    private val sha512Config8Digits = TotpConfig(
        secretKey = rfc6238Base32SecretSha512,
        algorithm = TotpAlgorithm.SHA512,
        digits = 8,
    )

    @Before
    fun setUp() {
        totpGenerator = TotpGeneratorImpl()
    }

    @Test
    fun generateCode_withRfc6238Sha1TestVectors8Digits_matchesOfficialSpecification() {
        // Official RFC 6238 Appendix B test vectors for 8 digits
        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 59L,
            )
        ).isEqualTo("94287082")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 1111111109L,
            )
        ).isEqualTo("07081804")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 1111111111L,
            )
        ).isEqualTo("14050471")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 1234567890L,
            )
        ).isEqualTo("89005924")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 2000000000L,
            )
        ).isEqualTo("69279037")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config8Digits,
                timeSeconds = 20000000000L,
            )
        ).isEqualTo("65353130")
    }

    @Test
    fun generateCode_withRfc6238Sha1TestVectors6Digits_matchesExpectedTruncation() {
        // Standard 6-digit truncation (last 6 digits of the 8-digit RFC vectors)
        assertThat(
            totpGenerator.generateCode(
                config = sha1Config6Digits,
                timeSeconds = 59L,
            )
        ).isEqualTo("287082")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config6Digits,
                timeSeconds = 1111111109L,
            )
        ).isEqualTo("081804")

        assertThat(
            totpGenerator.generateCode(
                config = sha1Config6Digits,
                timeSeconds = 1234567890L,
            )
        ).isEqualTo("005924")
    }

    @Test
    fun generateCode_withSha256Algorithm_generatesAccurateCode() {
        // Official RFC 6238 Table 1 test vectors for SHA256
        assertThat(
            totpGenerator.generateCode(
                config = sha256Config8Digits,
                timeSeconds = 59L,
            )
        ).isEqualTo("46119246")

        assertThat(
            totpGenerator.generateCode(
                config = sha256Config8Digits,
                timeSeconds = 1111111109L,
            )
        ).isEqualTo("68084774")

        assertThat(
            totpGenerator.generateCode(
                config = sha256Config8Digits,
                timeSeconds = 1234567890L,
            )
        ).isEqualTo("91819424")
    }

    @Test
    fun generateCode_withSha512Algorithm_generatesAccurateCode() {
        // Official RFC 6238 Table 1 test vectors for SHA512
        assertThat(
            totpGenerator.generateCode(
                config = sha512Config8Digits,
                timeSeconds = 59L,
            )
        ).isEqualTo("90693936")

        assertThat(
            totpGenerator.generateCode(
                config = sha512Config8Digits,
                timeSeconds = 1111111109L,
            )
        ).isEqualTo("25091201")

        assertThat(
            totpGenerator.generateCode(
                config = sha512Config8Digits,
                timeSeconds = 1234567890L,
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
    fun getRemainingSeconds_withNegativeTimestamp_returnsValidCountdown() {
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = -1L, period = 30)).isEqualTo(1)
        assertThat(totpGenerator.getRemainingSeconds(timeSeconds = -5L, period = 30)).isEqualTo(5)
    }

    @Test
    fun generateCode_withInvalidPeriodOrDigits_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                config = TotpConfig(secretKey = rfc6238Base32SecretSha1, period = 0),
                timeSeconds = 100L,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                config = TotpConfig(secretKey = rfc6238Base32SecretSha1, digits = 5),
                timeSeconds = 100L,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                config = TotpConfig(secretKey = rfc6238Base32SecretSha1, digits = 9),
                timeSeconds = 100L,
            )
        }
    }

    @Test
    fun isValidSecret_validatesCorrectly() {
        assertThat(totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP")).isTrue()
        assertThat(totpGenerator.isValidSecret("invalid_secret_1234!")).isFalse()
    }

    @Test
    fun isValidSecret_withSecretTooShortToDecode_returnsFalseSoGenerateCodeIsNeverReached() {
        assertThat(totpGenerator.isValidSecret("A")).isFalse()

        // the guard has to hold, because generateCode throws on an empty decoded key.
        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.generateCode(
                config = TotpConfig(secretKey = "A"),
                timeSeconds = 100L,
            )
        }
    }

    @Test
    fun isValidConfig_withOutOfRangeDigitsOrPeriod_returnsFalse() {
        val validConfig = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP")

        assertThat(totpGenerator.isValidConfig(validConfig)).isTrue()
        assertThat(totpGenerator.isValidConfig(validConfig.copy(digits = 5))).isFalse()
        assertThat(totpGenerator.isValidConfig(validConfig.copy(digits = 9))).isFalse()
        assertThat(totpGenerator.isValidConfig(validConfig.copy(period = 0))).isFalse()
        assertThat(totpGenerator.isValidConfig(validConfig.copy(period = -30))).isFalse()
        assertThat(totpGenerator.isValidConfig(validConfig.copy(secretKey = "A"))).isFalse()
    }

    @Test
    fun isValidConfig_rejectsEveryConfigThatWouldMakeGenerateCodeThrow() {
        val invalidConfigs = listOf(
            TotpConfig(secretKey = "A"),
            TotpConfig(secretKey = "JBSWY3DPEHPK3PXP", digits = 5),
            TotpConfig(secretKey = "JBSWY3DPEHPK3PXP", period = 0),
        )

        invalidConfigs.forEach { config ->
            assertThat(totpGenerator.isValidConfig(config)).isFalse()
            assertThrows(IllegalArgumentException::class.java) {
                totpGenerator.generateCode(config = config, timeSeconds = 100L)
            }
        }
    }

    @Test
    fun normalizeSecret_returnsCanonicalFormThatGeneratesTheSameCode() {
        val formatted = "jbsw y3dp-ehpk3pxp=="
        val normalized = totpGenerator.normalizeSecret(formatted)

        assertThat(normalized).isEqualTo("JBSWY3DPEHPK3PXP")

        val normalizedCode = totpGenerator.generateCode(
            config = TotpConfig(secretKey = normalized),
            timeSeconds = 100L,
        )
        val formattedCode = totpGenerator.generateCode(
            config = TotpConfig(secretKey = formatted),
            timeSeconds = 100L,
        )
        assertThat(normalizedCode).isEqualTo(formattedCode)
    }

    @Test
    fun generateCode_withSamePeriodButDifferentAlgorithm_producesDifferentCodes() {
        val sha1Code = totpGenerator.generateCode(
            config = TotpConfig(secretKey = rfc6238Base32SecretSha1),
            timeSeconds = 100L,
        )
        val sha256Code = totpGenerator.generateCode(
            config = TotpConfig(
                secretKey = rfc6238Base32SecretSha1,
                algorithm = TotpAlgorithm.SHA256,
            ),
            timeSeconds = 100L,
        )

        assertThat(sha1Code).isNotEqualTo(sha256Code)
    }

    @Test
    fun generateCode_withNonDefaultPeriod_rollsOverOnItsOwnSchedule() {
        val config = TotpConfig(secretKey = rfc6238Base32SecretSha1, period = 60)

        // inside one 60s window the code must not change, though a 30s window would have rolled.
        assertThat(totpGenerator.generateCode(config = config, timeSeconds = 0L))
            .isEqualTo(totpGenerator.generateCode(config = config, timeSeconds = 59L))
        assertThat(totpGenerator.generateCode(config = config, timeSeconds = 0L))
            .isNotEqualTo(totpGenerator.generateCode(config = config, timeSeconds = 60L))
    }

    @Test
    fun normalizeSecret_isIdempotent() {
        val once = totpGenerator.normalizeSecret("jbsw y3dp ehpk3pxp")

        assertThat(totpGenerator.normalizeSecret(once)).isEqualTo(once)
    }

    @Test
    fun generateCodeAndGetRemainingSeconds_withDefaultEpochSeconds_returnValidLiveValues() {
        val generatorInterface: TotpGenerator = totpGenerator
        val code = generatorInterface.generateCode(config = sha1Config6Digits)
        val remaining = generatorInterface.getRemainingSeconds(period = 30)

        assertThat(code).hasLength(6)
        assertThat(code.all { it.isDigit() }).isTrue()
        assertThat(remaining).isIn(1..30)
    }

    @Test
    fun getRemainingSeconds_withNonPositivePeriod_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.getRemainingSeconds(period = 0, timeSeconds = 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            totpGenerator.getRemainingSeconds(period = -15, timeSeconds = 100L)
        }
    }
}
