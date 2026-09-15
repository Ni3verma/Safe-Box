package com.andryoga.safebox.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import kotlinx.coroutines.delay

/**
 * RFC 6238 default time step.
 *
 * Owned by the UI layer because it drives the countdown ring and the code memoisation key. The
 * engine keeps its own default for code generation.
 */
const val TOTP_PERIOD_SECONDS = 30L

/**
 * Immutable snapshot of a live one-time code for the current time step.
 *
 * @property code Raw code, and the value that should be copied to the clipboard.
 * @property formattedCode Same code split into two halves (e.g. `"123 456"`) purely for on screen
 * readability. Never copy this, the space is not part of the code.
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
 * Extracted so that every surface showing a live code (the single record field and the records list
 * badge) shares one ticker implementation instead of each growing its own copy.
 *
 * The ticker lives inside this composable, so only the subtree that reads the returned state
 * recomposes each second. Callers inside a `LazyColumn` therefore get automatic cancellation when
 * the row scrolls out of composition. Deliberately not hoisted into a ViewModel: emitting the code
 * as list state would re-emit the whole record list every second and recompose every row,
 * including non authenticator ones.
 *
 * The code is keyed on the time step rather than the raw timestamp, so it is recomputed only when
 * the window actually rolls over and not on every tick.
 *
 * @param secretKey Base32 encoded secret seed.
 * @param totpGenerator Stateless RFC 6238 engine. Defaulted so callers stay previewable and
 * testable without reaching into DI from the composition.
 * @return The live code state, or null when [secretKey] cannot produce a code. Callers must render
 * a fallback for null rather than assuming a valid seed: restore-from-backup inserts rows straight
 * from the backup file without validating them, so a corrupt seed can reach the UI.
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
            delay(1000)
            value = System.currentTimeMillis() / 1000
        }
    }

    val code = remember(secretKey, epochSeconds / TOTP_PERIOD_SECONDS) {
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
