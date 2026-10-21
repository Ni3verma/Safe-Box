package com.andryoga.safebox.security.envelope

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordKdfTest {

    @Test
    fun deriveKey_withRfcVectors_shouldMatchExpectedKeys() {
        // RFC 6070 §2 PBKDF2-HMAC-SHA1 vectors (160-bit output) and corresponding SHA-256 vectors (256-bit output).
        val sha1SingleIteration = PasswordKdf.deriveKey(
            password = "password".toCharArray(),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 1,
            algorithm = "PBKDF2WithHmacSHA1",
            keyLengthBits = 160,
        )
        assertThat(sha1SingleIteration.toHex())
            .isEqualTo("0c60c80f961f0e71f3a9b524af6012062fe037a6")

        val sha1FourThousandNinetySix = PasswordKdf.deriveKey(
            password = "password".toCharArray(),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 4096,
            algorithm = "PBKDF2WithHmacSHA1",
            keyLengthBits = 160,
        )
        assertThat(sha1FourThousandNinetySix.toHex())
            .isEqualTo("4b007901b765489abead49d926f721d065a429c1")

        val sha256SingleIteration = PasswordKdf.deriveKey(
            password = "password".toCharArray(),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 1,
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            keyLengthBits = 256,
        )
        assertThat(sha256SingleIteration.toHex())
            .isEqualTo("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b")

        val sha256FourThousandNinetySix = PasswordKdf.deriveKey(
            password = "password".toCharArray(),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 4096,
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            keyLengthBits = 256,
        )
        assertThat(sha256FourThousandNinetySix.toHex())
            .isEqualTo("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a")
    }

    @Test
    fun kdfParams_encodeAndDecode_shouldRoundTripPreservingAllFields() {
        val original = PasswordKdf.newParams()

        val encoded = original.encode()
        val decoded = KdfParams.decode(encoded)

        assertThat(decoded).isEqualTo(original)
        assertThat(decoded.algorithm).isEqualTo(KdfParams.ALGORITHM_PBKDF2_SHA256)
        assertThat(decoded.iterations).isEqualTo(PasswordKdf.PBKDF2_ITERATIONS)
        assertThat(decoded.salt).isEqualTo(original.salt)
    }

    @Test
    fun kdfParamsDecode_withUnknownVersionOrAlgorithm_shouldFailClosed() {
        val validEncoded = PasswordKdf.newParams().encode()
        val unknownVersion = validEncoded.replaceFirst("v1|", "v2|")
        val unknownAlgorithm = validEncoded.replaceFirst("PBKDF2WithHmacSHA256", "Argon2id")
        val malformedParts = "v1|PBKDF2WithHmacSHA256|99000"
        val nonPositiveIterations = validEncoded.replaceFirst("|99000|", "|0|")

        assertThrows(IllegalArgumentException::class.java) {
            KdfParams.decode(unknownVersion)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KdfParams.decode(unknownAlgorithm)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KdfParams.decode(malformedParts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KdfParams.decode(nonPositiveIterations)
        }
    }

    @Test
    fun deriveKey_withPrecomposedAndCombiningAccent_shouldProduceSameKey() {
        val params = testParams()
        val precomposed = "Caf\u00E9#24".toCharArray()
        val combining = "Cafe\u0301#24".toCharArray()

        val keyPrecomposed = PasswordKdf.deriveKey(precomposed, params)
        val keyCombining = PasswordKdf.deriveKey(combining, params)

        assertThat(keyPrecomposed).isEqualTo(keyCombining)
    }

    @Test
    fun deriveKey_withAsciiAndFullWidthCharacters_shouldProduceSameKey() {
        val params = testParams()
        val ascii = "pass123".toCharArray()
        val fullWidth = "\uFF50\uFF41\uFF53\uFF53\uFF11\uFF12\uFF13".toCharArray()

        val keyAscii = PasswordKdf.deriveKey(ascii, params)
        val keyFullWidth = PasswordKdf.deriveKey(fullWidth, params)

        assertThat(keyAscii).isEqualTo(keyFullWidth)
    }

    @Test
    fun deriveKey_withFiLigatureAndSeparateLetters_shouldProduceSameKey() {
        val params = testParams()
        val separate = "finance1".toCharArray()
        val ligature = "\uFB01nance1".toCharArray()

        val keySeparate = PasswordKdf.deriveKey(separate, params)
        val keyLigature = PasswordKdf.deriveKey(ligature, params)

        assertThat(keySeparate).isEqualTo(keyLigature)
    }

    @Test
    fun deriveKey_withAsciiApostropheAndCurlyApostrophe_shouldProduceDifferentKeys() {
        val params = testParams()
        val straightApostrophe = "don't#24".toCharArray()
        val curlyApostrophe = "don\u2019t#24".toCharArray()

        val keyStraight = PasswordKdf.deriveKey(straightApostrophe, params)
        val keyCurly = PasswordKdf.deriveKey(curlyApostrophe, params)

        assertThat(keyStraight).isNotEqualTo(keyCurly)
    }

    @Test
    fun deriveKey_shouldClearCallerPasswordArrayAfterUse() {
        val params = testParams()
        val password = "Secret#Password99".toCharArray()

        PasswordKdf.deriveKey(password, params)

        assertThat(password.all { it == '\u0000' }).isTrue()
    }

    private fun testParams(): KdfParams = KdfParams(
        algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
        iterations = 1_000,
        salt = ByteArray(KdfParams.SALT_SIZE_BYTES) { index -> (index + 1).toByte() },
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
