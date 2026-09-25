package com.andryoga.safebox.upgradetest

import java.io.ByteArrayOutputStream

/**
 * RFC 4648 Base32 decoding, written out here so the harness never borrows the app's decoder.
 *
 * Group 5 judges the app's one-time codes against a value the harness computes itself. If that
 * value came from the app's own Base32 or TOTP code, a defect shared by both would agree with
 * itself and pass — which is the one outcome an independent check exists to rule out.
 */
internal object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BITS_PER_CHAR = 5
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

    /**
     * Decodes a Base32 string into the bytes it encodes.
     *
     * Characters are consumed five bits at a time into an accumulator, and a byte is emitted each
     * time eight bits are available. Trailing `=` padding is dropped, and any bits left over at the
     * end are discarded, which is what RFC 4648 prescribes for a well-formed encoding.
     *
     * @param encoded the Base32 text, upper or lower case
     * @return the decoded bytes
     * @throws IllegalArgumentException if a character is outside the Base32 alphabet
     */
    fun decode(encoded: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        encoded.trimEnd('=').forEach { char ->
            val value = ALPHABET.indexOf(char.uppercaseChar())
            require(value >= 0) { "'$char' is not a Base32 character" }
            buffer = (buffer shl BITS_PER_CHAR) or value
            bits += BITS_PER_CHAR
            if (bits >= BITS_PER_BYTE) {
                bits -= BITS_PER_BYTE
                out.write((buffer shr bits) and BYTE_MASK)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
