package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.totp.models.TotpUriParseResult
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parser for standard Key URI format (`otpauth://totp/...`) used in QR codes for Two-Factor Authentication.
 *
 * Extracts and normalizes the account/issuer label, Base32 secret key, hashing algorithm,
 * output digits, and time step period.
 */
object TotpUriParser {

    /**
     * Parses an `otpauth://totp/...` URI string into a [TotpUriParseResult].
     *
     * An absent `algorithm`, `digits` or `period` falls back to its [TotpDefaults] value, per the
     * Key URI spec. A value that is present but unsupported is rejected rather than defaulted.
     *
     * @param uriString Full raw URI scanned from a QR code or entered by the user.
     * @return [TotpUriParseResult.Success] with the parsed data, [TotpUriParseResult.NotTotpUri]
     * when the payload is not an `otpauth://` URI, or [TotpUriParseResult.Unsupported] when it is
     * one but cannot be used.
     */
    fun parse(uriString: String): TotpUriParseResult {
        val sanitizedUriString = uriString.trim().replace(" ", "%20")
        val uri = try {
            URI(sanitizedUriString)
        } catch (_: Exception) {
            return TotpUriParseResult.NotTotpUri
        }

        if (uri.scheme?.equals("otpauth", ignoreCase = true) != true) {
            return TotpUriParseResult.NotTotpUri
        }

        val type = uri.host ?: uri.authority
        if (type?.equals("totp", ignoreCase = true) != true) {
            return TotpUriParseResult.Unsupported(TotpUriError.UNSUPPORTED_OTP_TYPE)
        }

        val queryParams = parseQueryParams(uri.rawQuery)

        val rawSecret = queryParams.optionalParam("secret")
            ?: return TotpUriParseResult.Unsupported(TotpUriError.INVALID_SECRET)
        val cleanSecret = Base32Utils.sanitize(rawSecret)
        if (!Base32Utils.isValidBase32(cleanSecret)) {
            return TotpUriParseResult.Unsupported(TotpUriError.INVALID_SECRET)
        }

        val algorithmParam = queryParams.optionalParam("algorithm")
        val algorithm = if (algorithmParam == null) {
            TotpAlgorithm.SHA1
        } else {
            parseAlgorithm(algorithmParam)
                ?: return TotpUriParseResult.Unsupported(TotpUriError.UNSUPPORTED_ALGORITHM)
        }

        val digitsParam = queryParams.optionalParam("digits")
        val digits = if (digitsParam == null) {
            TotpDefaults.DIGITS
        } else {
            digitsParam.toIntOrNull()?.takeIf { it in TotpDefaults.SUPPORTED_DIGITS }
                ?: return TotpUriParseResult.Unsupported(TotpUriError.UNSUPPORTED_DIGITS)
        }

        val periodParam = queryParams.optionalParam("period")
        val period = if (periodParam == null) {
            TotpDefaults.PERIOD_SECONDS
        } else {
            periodParam.toIntOrNull()?.takeIf { it > 0 }
                ?: return TotpUriParseResult.Unsupported(TotpUriError.UNSUPPORTED_PERIOD)
        }

        val decodedLabel = uri.path?.trimStart('/') ?: ""
        val title = computeTitle(decodedLabel, queryParams["issuer"])

        return TotpUriParseResult.Success(
            ParsedTotpData(
                title = title,
                config = TotpConfig(
                    secretKey = cleanSecret,
                    algorithm = algorithm,
                    digits = digits,
                    period = period,
                ),
            ),
        )
    }

    /**
     * Maps a Key URI `algorithm` token to a supported [TotpAlgorithm].
     *
     * Matched against literals, not [TotpAlgorithm.entries] and [Enum.name], so the mapping
     * survives minification of the enum constant names.
     *
     * @param rawAlgorithm Raw parameter value from the URI, in any casing.
     * @return Matching algorithm, or null when Safe-Box cannot compute that hash.
     */
    private fun parseAlgorithm(rawAlgorithm: String): TotpAlgorithm? =
        when (rawAlgorithm.uppercase()) {
            "SHA1" -> TotpAlgorithm.SHA1
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> null
        }

    /**
     * Reads a query parameter, treating a blank value as absent.
     *
     * @param key Lowercase parameter name.
     * @return Trimmed value, or null when the parameter is missing or blank.
     */
    private fun Map<String, String>.optionalParam(key: String): String? =
        this[key]?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Parses the raw query string into a case-insensitive map of parameter key-value pairs.
     *
     * @param rawQuery The raw query string from the URI.
     * @return Map of decoded query parameter names (in lowercase) to their values.
     */
    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()

        return rawQuery.split("&")
            .mapNotNull { param ->
                val keyValue = param.split("=", limit = 2)
                if (keyValue.size == 2) {
                    val key = try {
                        URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name())
                    } catch (e: Exception) {
                        keyValue[0]
                    }
                    val value = try {
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
                    } catch (e: Exception) {
                        keyValue[1]
                    }
                    key.lowercase() to value
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Derives a clean, human-readable account title from the URI label and issuer parameter.
     *
     * @param decodedLabel Decoded label component from the URI path.
     * @param issuerParam Optional issuer query parameter value.
     * @return Formatted title (e.g. "Issuer - Account" or "Account").
     */
    private fun computeTitle(decodedLabel: String, issuerParam: String?): String {
        val trimmedIssuer = issuerParam?.trim()?.takeIf { it.isNotBlank() }

        if (decodedLabel.isBlank() && trimmedIssuer != null) {
            return trimmedIssuer
        }

        if (decodedLabel.contains(":")) {
            val parts = decodedLabel.split(":", limit = 2)
            val prefix = parts[0].trim()
            val account = parts[1].trim()

            val issuer = trimmedIssuer ?: prefix.takeIf { it.isNotBlank() }

            return when {
                issuer != null && account.isNotBlank() -> {
                    if (prefix.isNotBlank() && !issuer.equals(prefix, ignoreCase = true)) {
                        "$issuer ($prefix) - $account"
                    } else {
                        "$issuer - $account"
                    }
                }

                issuer != null -> issuer
                account.isNotBlank() -> account
                else -> "Authenticator Account"
            }
        }

        val label = decodedLabel.trim()
        return when {
            trimmedIssuer != null && label.isNotBlank() -> {
                if (!label.contains(trimmedIssuer, ignoreCase = true)) {
                    "$trimmedIssuer - $label"
                } else {
                    label
                }
            }

            label.isNotBlank() -> label
            trimmedIssuer != null -> trimmedIssuer
            else -> "Authenticator Account"
        }
    }
}
