package com.andryoga.safebox.upgradetest

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP with HMAC-SHA1, computed inside the harness and never by the app.
 *
 * This is the expected value Group 5 compares the app's displayed code against, so it has to be
 * trustworthy on its own terms. [checkKnownAnswers] therefore runs it against the RFC's published
 * test vectors before any comparison is made: a helper that is wrong in the same way as the app
 * would otherwise pass it, and one that is wrong in a different way would fail a correct app with
 * a message blaming the app.
 *
 * Only what the harness seeds is supported — SHA-1, the app's default for a hand-entered key.
 * SHA-256/512 seeds arrive only by QR code, which the harness cannot scan.
 */
internal object Rfc6238 {

    private const val HMAC_ALGORITHM = "HmacSHA1"
    private const val COUNTER_BYTES = 8
    private const val OFFSET_MASK = 0x0F
    private const val SIGN_MASK = 0x7F
    private const val BYTE_MASK = 0xFF

    /** Divisors for 6, 7 and 8 digit codes, indexed by digit count. */
    private val POWERS_OF_TEN = mapOf(6 to 1_000_000, 7 to 10_000_000, 8 to 100_000_000)

    /**
     * RFC 6238 Appendix B, SHA-1 column: the ASCII seed `12345678901234567890` and the 8-digit
     * code for each Unix time. The Base32 spelling is the one [SeedRecord.AUTHENTICATOR] seeds,
     * so the same string is proven to decode to the RFC's key.
     */
    private const val RFC_SEED_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private const val RFC_SEED_ASCII = "12345678901234567890"
    private const val RFC_DIGITS = 8
    private val RFC_VECTORS = mapOf(
        59L to "94287082",
        1_111_111_109L to "07081804",
        1_234_567_890L to "89005924",
        20_000_000_000L to "65353130",
    )

    /**
     * Computes the code for one time step.
     *
     * HOTP (RFC 4226) over the step counter: HMAC the big-endian counter, take four bytes at the
     * offset named by the low nibble of the last byte, clear the sign bit, and keep the low
     * [digits] decimal digits, zero-padded.
     *
     * @param key the decoded seed
     * @param step the time step, i.e. Unix seconds divided by the period
     * @param digits code length, 6 to 8
     * @return the code, exactly [digits] characters long
     */
    fun code(key: ByteArray, step: Long, digits: Int): String {
        val divisor = POWERS_OF_TEN[digits] ?: error("unsupported code length $digits")
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(key, HMAC_ALGORITHM)) }
        val hash = mac.doFinal(ByteBuffer.allocate(COUNTER_BYTES).putLong(step).array())
        val offset = hash.last().toInt() and OFFSET_MASK
        val binary = ((hash[offset].toInt() and SIGN_MASK) shl 24) or
            ((hash[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((hash[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (hash[offset + 3].toInt() and BYTE_MASK)
        return (binary % divisor).toString().padStart(digits, '0')
    }

    /**
     * Fails unless [Base32] and [code] reproduce RFC 6238's own answers.
     *
     * Cheap enough to run before every comparison, and it is what makes a Group 5 failure
     * unambiguous: once this has passed, a mismatch is the app's.
     */
    fun checkKnownAnswers() {
        val key = Base32.decode(RFC_SEED_BASE32)
        check(key.contentEquals(RFC_SEED_ASCII.toByteArray(Charsets.US_ASCII))) {
            "the harness's Base32 decoder does not decode the RFC 6238 seed correctly"
        }
        RFC_VECTORS.forEach { (time, expected) ->
            val actual = code(key, time / TotpDisplayCheck.PERIOD_SECONDS, RFC_DIGITS)
            check(actual == expected) {
                "the harness's TOTP helper gives $actual at T=$time, RFC 6238 says $expected"
            }
        }
    }
}
