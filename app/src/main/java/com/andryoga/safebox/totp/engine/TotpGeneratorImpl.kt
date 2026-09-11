package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpAlgorithm
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlin.math.pow

/**
 * Production implementation of [TotpGenerator] conforming to RFC 6238.
 *
 * Implements standard time-step counter computation, HMAC cryptographic hashing (SHA-1, SHA-256,
 * or SHA-512), and Dynamic Truncation (DT) extraction to generate numeric one-time codes.
 */
class TotpGeneratorImpl @Inject constructor() : TotpGenerator {

    /**
     * Computes the one-time code for the given secret key seed, epoch timestamp, and algorithm.
     *
     * @param secretBase32 Base32 encoded secret key.
     * @param timeSeconds Unix timestamp in seconds.
     * @param period Step duration in seconds (must be > 0).
     * @param digits Code length in digits (must be 6..8).
     * @param algorithm HMAC hashing algorithm.
     * @return Formatted numeric code zero-padded to the specified digit length.
     * @throws IllegalArgumentException If period <= 0, digits not in 6..8, or secret key bytes are empty.
     */
    override fun generateCode(
        secretBase32: String,
        timeSeconds: Long,
        period: Int,
        digits: Int,
        algorithm: TotpAlgorithm,
    ): String {
        require(period > 0) { "Period must be greater than 0" }
        require(digits in 6..8) { "Digits must be between 6 and 8" }

        val keyBytes = Base32Utils.decode(secretBase32)
        require(keyBytes.isNotEmpty()) { "Secret key bytes cannot be empty" }

        val timeCounter = timeSeconds / period
        val timeBytes = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(timeCounter).array()

        val mac = Mac.getInstance(algorithm.hmacAlgorithm)
        val macKey = SecretKeySpec(keyBytes, algorithm.hmacAlgorithm)
        mac.init(macKey)
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binaryCode =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

        val modulus = 10.0.pow(digits.toDouble()).toInt()
        val otp = binaryCode % modulus

        return otp.toString().padStart(digits, '0')
    }

    /**
     * Calculates the remaining lifetime of the current one-time code before rotation.
     *
     * @param timeSeconds Unix timestamp in seconds.
     * @param period Step duration in seconds.
     * @return Number of seconds remaining (1 to [period]).
     */
    override fun getRemainingSeconds(
        timeSeconds: Long,
        period: Int,
    ): Int {
        require(period > 0) { "Period must be greater than 0" }
        val elapsed = Math.floorMod(timeSeconds, period.toLong()).toInt()
        return if (elapsed == 0) period else period - elapsed
    }

    /**
     * Validates whether a given string is a syntactically valid Base32 secret key.
     *
     * @param secretBase32 Secret string to validate.
     * @return True if valid RFC 4648 Base32, false otherwise.
     */
    override fun isValidSecret(secretBase32: String): Boolean {
        return Base32Utils.isValidBase32(secretBase32)
    }
}
