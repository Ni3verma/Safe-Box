package com.andryoga.safebox.analytics

import com.andryoga.safebox.common.AnalyticsParam
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsParamsBuilderTest {

    @Test
    fun builder_addsVariousDataTypes_populatesInternalMapCorrectly() {
        val builder = AnalyticsParamsBuilder()

        builder.param(AnalyticsParam.MESSAGE, "Backup failed due to IO exception")
        builder.param(AnalyticsParam.DO_NOT_ASK_AGAIN, true)
        builder.param(AnalyticsParam.PERMISSION_ASKED_BEFORE, 3)
        builder.param(AnalyticsParam.REDIRECT_TO_SETTINGS, 1000000000L)
        builder.param(AnalyticsParam.VERSION, 2.5)

        val params = builder.params

        assertThat(params[AnalyticsParam.MESSAGE.paramName]).isEqualTo("Backup failed due to IO exception")
        assertThat(params[AnalyticsParam.DO_NOT_ASK_AGAIN.paramName]).isEqualTo(true)
        assertThat(params[AnalyticsParam.PERMISSION_ASKED_BEFORE.paramName]).isEqualTo(3)
        assertThat(params[AnalyticsParam.REDIRECT_TO_SETTINGS.paramName]).isEqualTo(1000000000L)
        assertThat(params[AnalyticsParam.VERSION.paramName]).isEqualTo(2.5)
        assertThat(params).hasSize(5)
    }

    @Test
    fun builder_duplicateKeys_overwritesWithLatestValue() {
        val builder = AnalyticsParamsBuilder()

        builder.param(AnalyticsParam.VERSION, 1.0)
        builder.param(AnalyticsParam.VERSION, 2.0)

        val params = builder.params

        assertThat(params[AnalyticsParam.VERSION.paramName]).isEqualTo(2.0)
        assertThat(params).hasSize(1)
    }
}
