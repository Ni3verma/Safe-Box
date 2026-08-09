package com.andryoga.safebox.totp.models

/**
 * Immutable data model representing the parsed components of an `otpauth://totp/...` Key URI.
 *
 * @property title Human-readable account or issuer label (e.g., "Google - alex@gmail.com").
 * @property secretKey Sanitized Base32 secret key seed used for HMAC generation.
 * @property algorithm Cryptographic hash function used for HMAC calculation (defaults to [TotpAlgorithm.SHA1]).
 * @property digits Number of output digits for the one-time password (typically 6, supports 6..8).
 * @property period Time step window in seconds (standard is 30 seconds).
 */
data class ParsedTotpData(
    val title: String,
    val secretKey: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
)
