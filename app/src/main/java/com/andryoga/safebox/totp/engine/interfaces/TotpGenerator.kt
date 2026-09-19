package com.andryoga.safebox.totp.engine.interfaces

import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.models.TotpConfig

/**
 * Contract for generating Time-Based One-Time Passwords (TOTP) conforming to RFC 6238.
 */
interface TotpGenerator {

    /**
     * Generates a deterministic time-based one-time password for the given config and timestamp.
     *
     * The seed and its generation parameters are taken together so a record's stored parameters can
     * never be silently replaced by engine defaults.
     *
     * @param config Seed plus the algorithm, digit count and time step to derive the code with.
     * @param timeSeconds Current unix timestamp in seconds (defaults to system epoch).
     * @return Formatted zero-padded string of digits representing the one-time password.
     */
    fun generateCode(
        config: TotpConfig,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
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
        period: Int = TotpDefaults.PERIOD_SECONDS,
    ): Int

    /**
     * Validates whether a given string is a syntactically valid Base32 secret key.
     *
     * @param secretBase32 Secret string to validate.
     * @return True if valid RFC 4648 Base32, false otherwise.
     */
    fun isValidSecret(secretBase32: String): Boolean

    /**
     * Validates that a whole config can actually produce a code.
     *
     * Checks the parameters as well as the seed, because [generateCode] throws on an unsupported
     * digit count or a non-positive period just as it does on an undecodable seed.
     *
     * @param config Seed and generation parameters to validate.
     * @return True when [generateCode] will succeed for this config.
     */
    fun isValidConfig(config: TotpConfig): Boolean

    /**
     * Converts a secret to the canonical form the engine decodes, so the same seed is never stored
     * under two different spellings.
     *
     * @param secretBase32 Secret as the user typed it or as an issuer formatted it.
     * @return Uppercase secret with whitespace, hyphens and trailing padding removed.
     */
    fun normalizeSecret(secretBase32: String): String
}
