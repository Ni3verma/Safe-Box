package com.andryoga.safebox.ui.core.appupdate

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the translation of Play's consent-sheet result codes into [UpdateFlowResult], including
 * the rule that unknown codes count as failures.
 */
class UpdateFlowResultTest {

    @Test
    fun resultOk_shouldMapToAccepted() {
        assertThat(UpdateFlowResult.fromResultCode(Activity.RESULT_OK))
            .isEqualTo(UpdateFlowResult.ACCEPTED)
    }

    @Test
    fun resultCanceled_shouldMapToCanceled() {
        assertThat(UpdateFlowResult.fromResultCode(Activity.RESULT_CANCELED))
            .isEqualTo(UpdateFlowResult.CANCELED)
    }

    @Test
    fun resultInAppUpdateFailed_shouldMapToFailed() {
        assertThat(UpdateFlowResult.fromResultCode(ActivityResult.RESULT_IN_APP_UPDATE_FAILED))
            .isEqualTo(UpdateFlowResult.FAILED)
    }

    @Test
    fun unknownResultCode_shouldMapToFailed() {
        assertThat(UpdateFlowResult.fromResultCode(UNKNOWN_RESULT_CODE))
            .isEqualTo(UpdateFlowResult.FAILED)
    }

    private companion object {
        const val UNKNOWN_RESULT_CODE = 99
    }
}
