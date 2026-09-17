package com.andryoga.safebox.totp.models

/**
 * Outcome of parsing an `otpauth://` Key URI.
 *
 * A result rather than a thrown exception because the three cases need opposite handling: an
 * unrelated QR code must be ignored so scanning continues, while an `otpauth://` URI Safe-Box
 * cannot use must stop the scan and explain itself.
 */
sealed interface TotpUriParseResult {

    /**
     * URI was a usable `otpauth://totp/` Key URI.
     *
     * @property data Title plus the seed and generation parameters the issuer encoded.
     */
    data class Success(val data: ParsedTotpData) : TotpUriParseResult

    /** Payload is not an `otpauth://` URI. Callers keep scanning without alerting the user. */
    data object NotTotpUri : TotpUriParseResult

    /**
     * Payload is an `otpauth://` URI that cannot be turned into a record.
     *
     * @property reason Which part of the URI Safe-Box rejected.
     */
    data class Unsupported(val reason: TotpUriError) : TotpUriParseResult
}
