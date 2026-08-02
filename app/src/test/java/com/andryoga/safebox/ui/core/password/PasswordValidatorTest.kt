package com.andryoga.safebox.ui.core.password

import com.andryoga.safebox.ui.signup.PasswordValidatorState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordValidatorTest {

    @Test
    fun blankPassword_shouldReturnEmptyPasswordState() {
        val result = PasswordValidator.validate("")

        assertThat(result).isEqualTo(PasswordValidatorState.EMPTY_PASSWORD)
    }

    @Test
    fun shortPassword_shouldReturnShortPasswordLengthState() {
        val result = PasswordValidator.validate("Ab@12")

        assertThat(result).isEqualTo(PasswordValidatorState.SHORT_PASSWORD_LENGTH)
    }

    @Test
    fun noUpperCasePassword_shouldReturnNotMixCaseState() {
        val result = PasswordValidator.validate("lowercase@123")

        assertThat(result).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
    }

    @Test
    fun noLowerCasePassword_shouldReturnNotMixCaseState() {
        val result = PasswordValidator.validate("UPPERCASE@123")

        assertThat(result).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
    }

    @Test
    fun lessNumericCountPassword_shouldReturnLessNumericCountState() {
        val result = PasswordValidator.validate("Abcdefg@1")

        assertThat(result).isEqualTo(PasswordValidatorState.LESS_NUMERIC_COUNT)
    }

    @Test
    fun noSpecialCharPassword_shouldReturnNoSpecialCharState() {
        val result = PasswordValidator.validate("Abcdefg123")

        assertThat(result).isEqualTo(PasswordValidatorState.NO_SPECIAL_CHAR)
    }

    @Test
    fun validPassword_shouldReturnPasswordIsOkState() {
        val result = PasswordValidator.validate("Valid@123")

        assertThat(result).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
    }
}
