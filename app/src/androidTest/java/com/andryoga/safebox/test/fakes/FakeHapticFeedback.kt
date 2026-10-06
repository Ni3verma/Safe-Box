package com.andryoga.safebox.test.fakes

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Recording [HapticFeedback] for Compose UI tests.
 *
 * The platform vibrator leaves no trace a test can read, so substituting this through
 * `LocalHapticFeedback` is the only way to assert that a gesture (the wrong-password shake, for
 * one) fired the haptic it promises, and fired it exactly once.
 */
class FakeHapticFeedback : HapticFeedback {

    private val _performed = mutableListOf<HapticFeedbackType>()

    /** Every haptic requested so far, in order. */
    val performed: List<HapticFeedbackType>
        get() = _performed

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        _performed += hapticFeedbackType
    }
}
