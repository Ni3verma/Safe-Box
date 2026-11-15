package com.andryoga.safebox.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HashingUtilsImplTest {

    @get:Rule
    val base64Rule = Base64Rule()

    private lateinit var hashingUtils: HashingUtilsImpl

    @Before
    fun setUp() {
        hashingUtils = HashingUtilsImpl()
    }

    @Test
    fun compareHash_withAHashInTheFormatOldReleasesWrote_returnsTrueForItsPassword() {
        assertThat(hashingUtils.compareHash(VECTOR_PASSWORD, VECTOR_HASH)).isTrue()
    }

    @Test
    fun compareHash_withIncorrectPassword_returnsFalse() {
        assertThat(hashingUtils.compareHash("Legacy#Pass13", VECTOR_HASH)).isFalse()
    }

    @Test
    fun compareHash_withEmptyPasswordVector_verifiesOnlyTheEmptyPassword() {
        assertThat(hashingUtils.compareHash("", EMPTY_PASSWORD_HASH)).isTrue()
        assertThat(hashingUtils.compareHash("nonEmpty", EMPTY_PASSWORD_HASH)).isFalse()
    }

    @Test
    fun compareHash_withMissingSeparator_throwsSecurityException() {
        val malformedHash = "NoSeparatorInThisHashString"

        assertThrows(SecurityException::class.java) {
            hashingUtils.compareHash("anyPassword", malformedHash)
        }
    }

    @Test
    fun compareHash_withMultipleSeparators_throwsSecurityException() {
        val malformedHash = "Part1|Part2|Part3"

        assertThrows(SecurityException::class.java) {
            hashingUtils.compareHash("anyPassword", malformedHash)
        }
    }

    @Test
    fun compareHash_withInvalidBase64Salt_throwsIllegalArgumentException() {
        val malformedHash = "ValidLookingHash|###NotBase64Salt###"

        assertThrows(IllegalArgumentException::class.java) {
            hashingUtils.compareHash("anyPassword", malformedHash)
        }
    }

    @Test
    fun compareHash_withEmptyHashString_throwsSecurityException() {
        val emptyHash = ""

        assertThrows(SecurityException::class.java) {
            hashingUtils.compareHash("anyPassword", emptyHash)
        }
    }

    @Test
    fun verifyHashingConstants_matchSecuritySpecifications() {
        assertThat(HashingUtilsImpl.Constants.iterationCount).isEqualTo(500)
        assertThat(HashingUtilsImpl.Constants.keyLength).isEqualTo(512)
    }

    private companion object {
        const val VECTOR_PASSWORD = "Legacy#Pass12"

        // PBKDF2WithHmacSHA1, 500 iterations, 512 bits, salt 00..0f, android.util.Base64.DEFAULT
        // (76-column lines, trailing newline): the exact bytes a pre-envelope build stored.
        const val VECTOR_HASH =
            "yJhIjp8QHFOkZAuuVz9XLC3vG/swUFZiqh8kyh3q/0Nnq4xxNFBUUBFN9Jvorbk5CqELpPIkYA1d\n" +
                "a82EB2js9Q==\n|AAECAwQFBgcICQoLDA0ODw==\n"

        const val EMPTY_PASSWORD_HASH =
            "y5IE7ac59iJz6HPI6UiZJo/zqjnqHyrRfrVMQ6wNvMOQaVILNQhcRuqIgSt7/xo5m9eDdyVVhOHZ\n" +
                "QKNRJJoKuQ==\n|AAECAwQFBgcICQoLDA0ODw==\n"
    }
}
