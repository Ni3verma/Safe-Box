package com.andryoga.safebox.ui.totp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
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
 * Drives a one second ticker and derives the current one-time code from [secretKey].
 *
 * Shared by every surface that shows a live code so they cannot drift apart.
 *
 * @param secretKey Base32 encoded secret seed.
 * @param totpGenerator Stateless RFC 6238 engine, defaulted to keep callers previewable.
 * @return Live code state, or null when [secretKey] cannot produce a code. Callers must render a
 * fallback for null, since restore-from-backup can insert an unvalidated seed.
 */
@Composable
fun rememberTotpCodeState(
    secretKey: String,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
): TotpCodeState? {
    val isValidSecret = remember(secretKey) { totpGenerator.isValidSecret(secretKey) }
    if (isValidSecret.not()) return null

    val epochSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(CommonConstants.TIME_1_SECOND)
            value = System.currentTimeMillis() / 1000
        }
    }

    // keyed on the time step so the code is recomputed only when the window rolls over.
    val code = remember(secretKey, epochSeconds / TotpDefaults.PERIOD_SECONDS) {
        totpGenerator.generateCode(secretBase32 = secretKey, timeSeconds = epochSeconds)
    }
    val remainingSeconds = remember(epochSeconds) {
        totpGenerator.getRemainingSeconds(timeSeconds = epochSeconds)
    }

    return remember(code, remainingSeconds) {
        TotpCodeState(
            code = code,
            formattedCode = "${code.take(code.length / 2)} ${code.drop(code.length / 2)}",
            remainingSeconds = remainingSeconds,
        )
    }
}
