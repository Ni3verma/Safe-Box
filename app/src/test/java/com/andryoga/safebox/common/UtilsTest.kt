package com.andryoga.safebox.common

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.common.Utils.isZero
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class UtilsTest {

    private val symmetricKeyUtils: SymmetricKeyUtils = mockk()

    @Test
    fun encryptNullableString_nullOrBlank_returnsNull() {
        val nullString: String? = null
        val emptyString = ""
        val blankString = "   \t\n  "

        assertThat(nullString.encryptNullableString(symmetricKeyUtils)).isNull()
        assertThat(emptyString.encryptNullableString(symmetricKeyUtils)).isNull()
        assertThat(blankString.encryptNullableString(symmetricKeyUtils)).isNull()

        verify(exactly = 0) { symmetricKeyUtils.encrypt(any()) }
    }

    @Test
    fun encryptNullableString_validString_delegatesToSymmetricKeyUtils() {
        val input = "SafeBox@SecretPassword2026"
        every { symmetricKeyUtils.encrypt(input) } returns "ENC_$input"

        val result = input.encryptNullableString(symmetricKeyUtils)

        assertThat(result).isEqualTo("ENC_$input")
        verify(exactly = 1) { symmetricKeyUtils.encrypt(input) }
    }

    @Test
    fun decryptNullableString_nullOrBlank_returnsNull() {
        val nullString: String? = null
        val emptyString = ""
        val blankString = "   \t\n  "

        assertThat(nullString.decryptNullableString(symmetricKeyUtils)).isNull()
        assertThat(emptyString.decryptNullableString(symmetricKeyUtils)).isNull()
        assertThat(blankString.decryptNullableString(symmetricKeyUtils)).isNull()

        verify(exactly = 0) { symmetricKeyUtils.decrypt(any()) }
    }

    @Test
    fun decryptNullableString_validString_delegatesToSymmetricKeyUtils() {
        val input = "ENC_SafeBox@SecretPassword2026"
        every { symmetricKeyUtils.decrypt(input) } returns "SafeBox@SecretPassword2026"

        val result = input.decryptNullableString(symmetricKeyUtils)

        assertThat(result).isEqualTo("SafeBox@SecretPassword2026")
        verify(exactly = 1) { symmetricKeyUtils.decrypt(input) }
    }

    @Test
    fun isZero_withNullAndZero_evaluatesTrue() {
        val nullInt: Int? = null
        val zeroInt: Int? = 0

        assertThat(nullInt.isZero()).isTrue()
        assertThat(zeroInt.isZero()).isTrue()
    }

    @Test
    fun isZero_withNonZeroInteger_evaluatesFalse() {
        val positiveInt: Int? = 1
        val negativeInt: Int? = -5
        val largeInt: Int? = 1000

        assertThat(positiveInt.isZero()).isFalse()
        assertThat(negativeInt.isZero()).isFalse()
        assertThat(largeInt.isZero()).isFalse()
    }

    @Test
    fun getFormattedDate_validDate_formatsAccordingToPattern() {
        // Date corresponding to Nov 14, 2023 22:13:20 UTC (1700000000000L)
        val fixedDate = Date(1700000000000L)
        val customPattern = "yyyyMMddHHmmss"

        val formattedDate = Utils.getFormattedDate(fixedDate, customPattern)

        assertThat(formattedDate).matches("^\\d{14}$")
    }

    @Test
    fun crashInDebugBuild_inDebugBuild_throwsDebugFatalException() {
        val errorMessage = "Critical assertion failure in debug mode"

        val exception = assertThrows(Exceptions.DebugFatalException::class.java) {
            Utils.crashInDebugBuild(errorMessage)
        }

        assertThat(exception.message).contains(errorMessage)
    }
}
