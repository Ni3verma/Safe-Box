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
    fun hash_returnsFormattedStringContainingSeparator() {
        val password = "MasterPassword123!"

        val hashResult = hashingUtils.hash(password)
        val parts = hashResult.split("|")

        assertThat(parts).hasSize(2)
        assertThat(parts[0]).isNotEmpty()
        assertThat(parts[1]).isNotEmpty()
    }

    @Test
    fun compareHash_withMatchingPassword_returnsTrue() {
        val password = "StrongSafeBoxPassword#2026"
        val hashResult = hashingUtils.hash(password)

        val isMatch = hashingUtils.compareHash(password, hashResult)

        assertThat(isMatch).isTrue()
    }

    @Test
    fun compareHash_withIncorrectPassword_returnsFalse() {
        val password = "CorrectPassword"
        val wrongPassword = "WrongPassword"
        val hashResult = hashingUtils.hash(password)

        val isMatch = hashingUtils.compareHash(wrongPassword, hashResult)

        assertThat(isMatch).isFalse()
    }

    @Test
    fun consecutiveHashCalls_forSamePassword_produceDistinctHashesDueToRandomSalt() {
        val password = "UserPassword"

        val hashFirst = hashingUtils.hash(password)
        val hashSecond = hashingUtils.hash(password)

        assertThat(hashFirst).isNotEqualTo(hashSecond)
    }

    @Test
    fun compareHash_successfullyVerifiesBothDistinctHashesOfSamePassword() {
        val password = "UserPassword"

        val hashFirst = hashingUtils.hash(password)
        val hashSecond = hashingUtils.hash(password)

        assertThat(hashingUtils.compareHash(password, hashFirst)).isTrue()
        assertThat(hashingUtils.compareHash(password, hashSecond)).isTrue()
    }

    @Test
    fun hashAndCompareHash_withEmptyPassword_verifiesSuccessfully() {
        val emptyPassword = ""
        val hashResult = hashingUtils.hash(emptyPassword)

        assertThat(hashingUtils.compareHash(emptyPassword, hashResult)).isTrue()
        assertThat(hashingUtils.compareHash("nonEmpty", hashResult)).isFalse()
    }

    @Test
    fun hashAndCompareHash_withUnicodeAndSpecialCharacters_verifiesSuccessfully() {
        val unicodePassword = "🔑 P@ssw0rd 日本語 café 🚀"
        val hashResult = hashingUtils.hash(unicodePassword)

        assertThat(hashingUtils.compareHash(unicodePassword, hashResult)).isTrue()
        assertThat(hashingUtils.compareHash("🔑 P@ssw0rd 日本語 café", hashResult)).isFalse()
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
        assertThat(HashingUtilsImpl.Constants.saltSize).isEqualTo(16)
    }
}
