package com.andryoga.safebox.totp.engine

import java.io.ByteArrayOutputStream

/**
 * Utility object for RFC 4648 Base32 decoding and validation.
 *
 * Implements decoding for the standard 32-character alphabet (A-Z, 2-7) with support for
 * whitespace stripping, hyphens, and padding character normalization.
 */
object Base32Utils {
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Decodes an RFC 4648 Base32 encoded string into its raw byte representation.
     *
     * @param encodedString Base32 encoded text (case-insensitive, ignores spaces and hyphens).
     * @return Decoded byte array.
     * @throws IllegalArgumentException If any illegal character outside the Base32 alphabet is encountered.
     */
    fun decode(encodedString: String): ByteArray {
        val sanitized = sanitize(encodedString)
        if (sanitized.isEmpty()) {
            return ByteArray(0)
        }

        val outputStream = ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0

        for (char in sanitized) {
            val charIndex = BASE32_CHARS.indexOf(char)
            if (charIndex == -1) {
                throw IllegalArgumentException("Invalid Base32 character encountered: $char")
            }

            buffer = (buffer shl 5) or charIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                val byteValue = (buffer shr (bitsLeft - 8)) and 0xFF
                outputStream.write(byteValue)
                bitsLeft -= 8
            }
        }

        return outputStream.toByteArray()
    }

    /**
     * Checks if the provided string contains only valid RFC 4648 Base32 characters after sanitization.
     *
     * @param encodedString String to validate.
     * @return True if valid, false if blank or contains invalid characters.
     */
    fun isValidBase32(encodedString: String): Boolean {
        val sanitized = sanitize(encodedString)
        if (sanitized.isEmpty()) return false
        return sanitized.all { BASE32_CHARS.indexOf(it) != -1 }
    }

    private fun sanitize(encodedString: String): String {
        return encodedString
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
            .uppercase()
    }
}
