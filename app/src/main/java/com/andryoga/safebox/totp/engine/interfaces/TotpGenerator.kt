package com.andryoga.safebox.totp.engine.interfaces

import com.andryoga.safebox.totp.models.TotpAlgorithm

/**
 * Contract for generating Time-Based One-Time Passwords (TOTP) conforming to RFC 6238.
 */
interface TotpGenerator {

    /**
     * Generates a deterministic time-based one-time password code for the given secret and timestamp.
     *
     * @param secretBase32 Base32 encoded secret key seed.
     * @param timeSeconds Current unix timestamp in seconds (defaults to system epoch).
     * @param period Time step window in seconds (default is 30s).
     * @param digits Length of the returned OTP code (typically 6, supports 6..8).
     * @param algorithm HMAC hashing algorithm (default is [TotpAlgorithm.SHA1]).
     * @return Formatted zero-padded string of digits representing the one-time password.
     */
    fun generateCode(
        secretBase32: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        period: Int = 30,
        digits: Int = 6,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    ): String

    /**
     * Calculates the remaining seconds before the current TOTP time step window expires.
     *
     * @param timeSeconds Current unix timestamp in seconds.
     * @param period Time step window duration in seconds (default is 30s).
     * @return Remaining seconds until the code rotates (1 to [period]).
     */
    fun getRemainingSeconds(
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        period: Int = 30,
    ): Int

    /**
     * Validates whether a given string is a syntactically valid Base32 secret key.
     *
     * @param secretBase32 Secret string to validate.
     * @return True if valid RFC 4648 Base32, false otherwise.
     */
    fun isValidSecret(secretBase32: String): Boolean
}
