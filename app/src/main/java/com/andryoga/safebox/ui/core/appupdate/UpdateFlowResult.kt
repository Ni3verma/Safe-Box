package com.andryoga.safebox.ui.core.appupdate

import android.app.Activity

/**
 * Outcome of Play's flexible-update consent sheet. The UI translates the raw activity result code
 * into this, so [InAppUpdateViewModel] does not depend on Android result codes.
 */
enum class UpdateFlowResult {
    ACCEPTED,
    CANCELED,
    FAILED,
    ;

    companion object {
        /**
         * Any code other than OK or cancelled counts as a failure. Play documents
         * `RESULT_IN_APP_UPDATE_FAILED`, and folding unknown codes in keeps the funnel summing up:
         * show = accept + cancel + failed.
         *
         * @param resultCode Result code returned by Play's consent sheet.
         * @return The matching outcome.
         */
        fun fromResultCode(resultCode: Int): UpdateFlowResult = when (resultCode) {
            Activity.RESULT_OK -> ACCEPTED
            Activity.RESULT_CANCELED -> CANCELED
            else -> FAILED
        }
    }
}
