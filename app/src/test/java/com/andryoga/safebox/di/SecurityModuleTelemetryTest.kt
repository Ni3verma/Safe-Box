package com.andryoga.safebox.di

import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityModuleTelemetryTest {

    private val analyticsHelper = FakeAnalyticsHelper()

    @Test
    fun logKeystoreKeyCreated_whenVaultAlreadyExists_shouldFlagTheDataLossSignature() {
        logKeystoreKeyCreated(analyticsHelper, dbExists = true)

        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.KEYSTORE_KEY_CREATED)
        assertThat(event.params[AnalyticsParam.DB_EXISTS.paramName]).isEqualTo(true)
    }

    @Test
    fun logKeystoreKeyCreated_onFirstLaunch_shouldReportNoExistingVault() {
        logKeystoreKeyCreated(analyticsHelper, dbExists = false)

        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.KEYSTORE_KEY_CREATED)
        assertThat(event.params[AnalyticsParam.DB_EXISTS.paramName]).isEqualTo(false)
    }
}
