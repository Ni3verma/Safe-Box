package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpAlgorithm
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
     * Parses an `otpauth://totp/...` URI string into a [ParsedTotpData] model.
     *
     * @param uriString Full raw URI scanned from a QR code or entered by the user.
     * @return [ParsedTotpData] containing validated title, cleaned secret, algorithm, digits, and period.
     * @throws IllegalArgumentException If URI is malformed, scheme is not 'otpauth', type is not 'totp',
     * or the secret key parameter is missing or invalid Base32.
     */
    fun parse(uriString: String): ParsedTotpData {
        val sanitizedUriString = uriString.trim().replace(" ", "%20")
        val uri = try {
            URI(sanitizedUriString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed URI: $uriString", e)
        }

        require(uri.scheme?.equals("otpauth", ignoreCase = true) == true) {
            "Invalid URI scheme. Expected 'otpauth', found: '${uri.scheme}'"
        }

        val type = uri.host ?: uri.authority
        require(type?.equals("totp", ignoreCase = true) == true) {
            "Unsupported OTP type: '$type'. Only 'totp' is supported."
        }

        val queryParams = parseQueryParams(uri.rawQuery)

        val rawSecret = queryParams["secret"]
        require(!rawSecret.isNullOrBlank()) {
            "Missing 'secret' query parameter in OTP URI"
        }
        val cleanSecret = rawSecret.replace(" ", "").replace("-", "").uppercase()
        require(Base32Utils.isValidBase32(cleanSecret)) {
            "Invalid Base32 secret key in OTP URI: $rawSecret"
        }

        val rawPath = uri.path?.trimStart('/') ?: ""
        val decodedLabel = try {
            URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            rawPath
        }

        val issuerParam = queryParams["issuer"]
        val title = computeTitle(decodedLabel, issuerParam)

        val algorithm = when (queryParams["algorithm"]?.uppercase()) {
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> TotpAlgorithm.SHA1
        }

        val digits = queryParams["digits"]?.toIntOrNull()?.let {
            if (it in 6..8) it else 6
        } ?: 6

        val period = queryParams["period"]?.toIntOrNull()?.let {
            if (it > 0) it else 30
        } ?: 30

        return ParsedTotpData(
            title = title,
            secretKey = cleanSecret,
            algorithm = algorithm,
            digits = digits,
            period = period,
        )
    }

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

    private fun computeTitle(decodedLabel: String, issuerParam: String?): String {
        if (decodedLabel.isBlank() && !issuerParam.isNullOrBlank()) {
            return issuerParam.trim()
        }

        if (decodedLabel.contains(":")) {
            val parts = decodedLabel.split(":", limit = 2)
            val prefix = parts[0].trim()
            val account = parts[1].trim()

            val issuer = issuerParam?.trim() ?: prefix
            return if (account.isNotBlank()) {
                if (issuer.equals(prefix, ignoreCase = true)) {
                    "$issuer - $account"
                } else {
                    "$issuer ($prefix) - $account"
                }
            } else {
                issuer
            }
        }

        val label = decodedLabel.trim()
        val issuer = issuerParam?.trim()
        return if (!issuer.isNullOrBlank() && !label.contains(issuer, ignoreCase = true)) {
            "$issuer - $label"
        } else if (label.isNotBlank()) {
            label
        } else {
            issuer ?: "Authenticator Account"
        }
    }
}
