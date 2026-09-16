package com.andryoga.safebox.totp.models

/**
 * Reasons an `otpauth://` URI cannot be turned into a usable authenticator record.
 *
 * Every rejection the parser can produce is enumerated here so the scanner can map each one to a
 * user-facing explanation and to an analytics reason, with the compiler enforcing that both
 * mappings stay complete.
 */
enum class TotpUriError {
    /** URI is `otpauth://` but not the time based (`totp`) variant. */
    UNSUPPORTED_OTP_TYPE,

    /** `secret` is absent, blank, or not decodable Base32. */
    INVALID_SECRET,

    /** `algorithm` is present but names a hash Safe-Box cannot compute. */
    UNSUPPORTED_ALGORITHM,

    /** `digits` is present but is not a code length the generator supports. */
    UNSUPPORTED_DIGITS,

    /** `period` is present but is not a positive number of seconds. */
    UNSUPPORTED_PERIOD,
}
