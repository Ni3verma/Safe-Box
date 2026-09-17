package com.andryoga.safebox.totp.models

/**
 * Reasons an `otpauth://` URI cannot be turned into a usable authenticator record.
 *
 * Enumerating them lets the scanner map each to an explanation and an analytics reason, with the
 * compiler enforcing that both mappings stay complete.
 */
enum class TotpUriError {
    /**
     * Payload declares the `otpauth` scheme but is not a well formed URI, so nothing after the
     * scheme could be read. Typically an illegal character such as `^`, `|` or a stray `%` left
     * unescaped in the issuer or account label.
     */
    MALFORMED_URI,

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
