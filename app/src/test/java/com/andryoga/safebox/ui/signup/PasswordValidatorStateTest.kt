package com.andryoga.safebox.ui.signup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordValidatorStateTest {

    @Test
    fun passwordValidatorState_containsAllExpectedEnumVariants() {
        val enumNames = PasswordValidatorState.entries.map { it.name }

        assertThat(enumNames).containsExactly(
            "INITIAL_STATE",
            "EMPTY_PASSWORD",
            "SHORT_PASSWORD_LENGTH",
            "NO_SPECIAL_CHAR",
            "NOT_MIX_CASE",
            "LESS_NUMERIC_COUNT",
            "PASSWORD_IS_OK"
        ).inOrder()
    }
}
