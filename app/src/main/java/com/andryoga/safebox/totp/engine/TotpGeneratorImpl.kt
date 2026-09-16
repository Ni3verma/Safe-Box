package com.andryoga.safebox.totp.engine

import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
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
     * Computes the one-time code for the given config and epoch timestamp.
     *
     * @param config Seed plus the algorithm, digit count and time step to derive the code with.
     * @param timeSeconds Unix timestamp in seconds.
     * @return Formatted numeric code zero-padded to the configured digit length.
     * @throws IllegalArgumentException If period <= 0, digits not in 6..8, or secret key bytes are empty.
     */
    override fun generateCode(
        config: TotpConfig,
        timeSeconds: Long,
    ): String {
        val period = config.period
        val digits = config.digits
        require(period > 0) { "Period must be greater than 0" }
        require(digits in TotpDefaults.SUPPORTED_DIGITS) { "Digits must be between 6 and 8" }

        val keyBytes = Base32Utils.decode(config.secretKey)
        require(keyBytes.isNotEmpty()) { "Secret key bytes cannot be empty" }

        val timeCounter = Math.floorDiv(timeSeconds, period.toLong())
        val timeBytes = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(timeCounter).array()

        val mac = Mac.getInstance(config.algorithm.hmacAlgorithm)
        val macKey = SecretKeySpec(keyBytes, config.algorithm.hmacAlgorithm)
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

    /**
     * Validates the seed together with the parameters [generateCode] would use.
     *
     * @param config Seed and generation parameters to validate.
     * @return True when the seed decodes and both digits and period are in range.
     */
    override fun isValidConfig(config: TotpConfig): Boolean {
        return isValidSecret(config.secretKey) &&
                config.digits in TotpDefaults.SUPPORTED_DIGITS &&
                config.period > 0
    }

    /**
     * Converts a secret to the canonical form the engine decodes.
     *
     * @param secretBase32 Secret as the user typed it or as an issuer formatted it.
     * @return Uppercase secret with whitespace, hyphens and trailing padding removed.
     */
    override fun normalizeSecret(secretBase32: String): String {
        return Base32Utils.sanitize(secretBase32)
    }
}
