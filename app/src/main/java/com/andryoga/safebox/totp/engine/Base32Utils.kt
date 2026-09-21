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

    // Each character carries 5 bits, so two are the fewest that complete a byte. A lone character
    // contributes 5 bits that decode() discards, returning an empty key the generator cannot use.
    private const val MIN_ENCODED_LENGTH = 2

    // 8 characters carry 40 bits, the smallest whole number of bytes a Base32 group can encode.
    private const val CHARS_PER_QUANTUM = 8

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
                throw IllegalArgumentException("Invalid Base32 character encountered")
            }

            buffer = (buffer shl 5) or charIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                val byteValue = (buffer shr (bitsLeft - 8)) and 0xFF
                outputStream.write(byteValue)
                bitsLeft -= 8
                buffer = buffer and ((1 shl bitsLeft) - 1)
            }
        }

        return outputStream.toByteArray()
    }

    /**
     * Checks if the provided string contains only valid RFC 4648 Base32 characters after sanitization,
     * is long enough to decode into at least one byte, and has a length a Base32 encoder could
     * actually produce.
     *
     * @param encodedString String to validate.
     * @return True if valid, false if too short, wrongly sized, or containing invalid characters.
     */
    fun isValidBase32(encodedString: String): Boolean {
        val sanitized = sanitize(encodedString)
        if (sanitized.length < MIN_ENCODED_LENGTH) return false
        when (sanitized.length % CHARS_PER_QUANTUM) {
            // A full quantum is 8 characters carrying 5 bytes, so a trailing partial group can only
            // be 2, 4, 5 or 7 characters. 1, 3 or 6 leftover characters carry bits that cannot
            // complete a byte, so decode() drops them: such a seed would be accepted and then
            // silently generate codes the issuer can never match.
            1, 3, 6 -> return false
        }
        return sanitized.all { BASE32_CHARS.indexOf(it) != -1 }
    }

    /**
     * Normalizes a Base32 string by stripping whitespace, separators, and padding, and converting
     * to uppercase.
     *
     * `+` is dropped for the same reason as `-`: neither is in the RFC 4648 alphabet, so both can
     * only ever be readability separators. It matters for `+` in particular because the Key URI
     * query decoder follows RFC 3986 and leaves a literal `+` in place, so an issuer that writes a
     * spaced seed in form-encoded style would otherwise hand us a seed that fails validation.
     *
     * @param encodedString Raw Base32 string.
     * @return Normalized Base32 string.
     */
    internal fun sanitize(encodedString: String): String {
        return encodedString
            .filterNot { it.isWhitespace() || it == '-' || it == '+' }
            .trimEnd('=')
            .uppercase()
    }
}
