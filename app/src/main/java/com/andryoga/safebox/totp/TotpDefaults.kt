package com.andryoga.safebox.totp

/**
 * RFC 6238 / Key URI defaults applied whenever an `otpauth://` URI omits a parameter. A parameter
 * that is present but out of range is rejected by the parser instead of being defaulted.
 *
 * Lives inside the `totp` package rather than app level constants so the engine keeps depending on
 * nothing outside itself, and UI surfaces depend inwards on the engine.
 */
object TotpDefaults {
    /** Time step window, in seconds. */
    const val PERIOD_SECONDS = 30

    /** Number of digits in a generated code. */
    const val DIGITS = 6

    /** Range of code lengths the generator accepts. */
    val SUPPORTED_DIGITS = 6..8
}
