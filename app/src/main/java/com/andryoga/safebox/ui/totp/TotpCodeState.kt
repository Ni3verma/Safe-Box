package com.andryoga.safebox.ui.totp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
import kotlinx.coroutines.delay

/**
 * Snapshot of a live one-time code for the current time step.
 *
 * @property code Raw code, and the value to copy.
 * @property formattedCode Code split into two halves for readability, e.g. `"123 456"`.
 * @property remainingSeconds Seconds left before the code rolls over.
 */
data class TotpCodeState(
    val code: String,
    val formattedCode: String,
    val remainingSeconds: Int,
)

/**
 * Drives a one second ticker and derives the current one-time code from [config].
 *
 * Shared by every surface that shows a live code so they cannot drift apart.
 *
 * @param config Seed plus the record's own generation parameters.
 * @param totpGenerator Stateless RFC 6238 engine, defaulted to keep callers previewable.
 * @return Live code state, or null when [config] cannot produce a code. Callers must render a
 * fallback for null, since restore-from-backup can insert an unvalidated seed.
 */
@Composable
fun rememberTotpCodeState(
    config: TotpConfig,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
): TotpCodeState? {
    val isValidConfig = remember(config) { totpGenerator.isValidConfig(config) }
    if (isValidConfig.not()) return null

    val epochSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            val nowMillis = System.currentTimeMillis()
            value = nowMillis / 1000
            // aligned to the next whole second so independent tickers stay in step with each other
            // and with the real rollover, instead of drifting by their start offset.
            delay(CommonConstants.TIME_1_SECOND - nowMillis % CommonConstants.TIME_1_SECOND)
        }
    }

    // keyed on the time step so the code is recomputed only when the window rolls over.
    val code = remember(config, epochSeconds / config.period) {
        totpGenerator.generateCode(config = config, timeSeconds = epochSeconds)
    }
    val remainingSeconds = remember(epochSeconds, config.period) {
        totpGenerator.getRemainingSeconds(timeSeconds = epochSeconds, period = config.period)
    }

    return remember(code, remainingSeconds) {
        TotpCodeState(
            code = code,
            formattedCode = "${code.take(code.length / 2)} ${code.drop(code.length / 2)}",
            remainingSeconds = remainingSeconds,
        )
    }
}
