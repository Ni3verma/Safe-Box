package com.andryoga.safebox.totp.models

import com.andryoga.safebox.totp.TotpDefaults

/**
 * Everything needed to derive a one-time code for a single authenticator record.
 *
 * These four values always travel together: a seed generates the wrong code when paired with the
 * wrong algorithm, digit count or time step.
 *
 * @property secretKey Canonical Base32 secret seed, already normalized by the engine.
 * @property algorithm HMAC hash used for code derivation.
 * @property digits Length of the generated code, within [TotpDefaults.SUPPORTED_DIGITS].
 * @property period Time step window in seconds.
 */
data class TotpConfig(
    val secretKey: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = TotpDefaults.DIGITS,
    val period: Int = TotpDefaults.PERIOD_SECONDS,
)
