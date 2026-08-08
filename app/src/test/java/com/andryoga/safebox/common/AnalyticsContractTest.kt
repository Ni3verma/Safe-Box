package com.andryoga.safebox.common

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Contract verification test suite asserting all analytics keys and params comply
 * with Google Firebase Analytics constraints.
 */
class AnalyticsContractTest {

    private val firebaseNamingRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    @Test
    fun allAnalyticsKeyNames_mustNotExceed40Characters() {
        AnalyticsKey.entries.forEach { key ->
            assertWithMessage("AnalyticsKey '${key.name}' eventName '${key.eventName}' exceeds 40 characters")
                .that(key.eventName.length)
                .isAtMost(40)
        }
    }

    @Test
    fun allAnalyticsKeyNames_mustFollowFirebaseNamingConventions() {
        AnalyticsKey.entries.forEach { key ->
            assertWithMessage("AnalyticsKey '${key.name}' eventName '${key.eventName}' contains invalid Firebase characters")
                .that(key.eventName.matches(firebaseNamingRegex))
                .isTrue()
        }
    }

    @Test
    fun allAnalyticsParamNames_mustNotExceed40Characters() {
        AnalyticsParam.entries.forEach { param ->
            assertWithMessage("AnalyticsParam '${param.name}' paramName '${param.paramName}' exceeds 40 characters")
                .that(param.paramName.length)
                .isAtMost(40)
        }
    }

    @Test
    fun allAnalyticsParamNames_mustFollowFirebaseNamingConventions() {
        AnalyticsParam.entries.forEach { param ->
            assertWithMessage("AnalyticsParam '${param.name}' paramName '${param.paramName}' contains invalid Firebase characters")
                .that(param.paramName.matches(firebaseNamingRegex))
                .isTrue()
        }
    }
}
