package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig

/**
 * Deterministic [TotpGenerator] for Compose UI tests.
 *
 * The real engine derives its output from the wall clock, so a test asserting on a rendered code
 * would race the time step rollover. This fake returns fixed values instead, which lets a test
 * pin the exact digits on screen and the exact countdown position.
 *
 * @property code Code returned by [generateCode] regardless of the config or timestamp.
 * @property remainingSeconds Value returned by [getRemainingSeconds].
 * @property isValid Whether the config is reported as usable, which drives the invalid-seed
 * fallback branch of the composables under test.
 */
class FakeTotpGenerator(
    private val code: String = "123456",
    private val remainingSeconds: Int = 20,
    private val isValid: Boolean = true,
) : TotpGenerator {

    override fun generateCode(config: TotpConfig, timeSeconds: Long): String = code

    override fun getRemainingSeconds(timeSeconds: Long, period: Int): Int = remainingSeconds

    override fun isValidSecret(secretBase32: String): Boolean = isValid

    override fun isValidConfig(config: TotpConfig): Boolean = isValid

    override fun normalizeSecret(secretBase32: String): String = secretBase32.uppercase()
}
