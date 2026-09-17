package com.andryoga.safebox.totp.models

/**
 * Immutable data model representing the parsed components of an `otpauth://totp/...` Key URI.
 *
 * @property title Human-readable account or issuer label (e.g., "Google - alex@gmail.com").
 * @property config Seed plus the generation parameters the issuer encoded in the URI.
 */
data class ParsedTotpData(
    val title: String,
    val config: TotpConfig,
)
