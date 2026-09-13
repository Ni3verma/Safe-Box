package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.ui.core.CircularCountdownRing
import com.andryoga.safebox.ui.core.rememberCopyToClipboardAction
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import kotlinx.coroutines.delay

/**
 * View mode field rendering the live rolling 2FA code derived from a stored Base32 seed.
 *
 * Styled like every other read only field in `SingleRecordScreen` (label above value) so the
 * authenticator record does not visually break the field list, with the value replaced by the
 * rolling code plus its countdown ring.
 *
 * The 1 second ticker lives in this composable via [produceState] so only this subtree recomposes
 * every second, leaving the rest of the screen untouched. The code itself is keyed on the time step
 * so it is recomputed only when the window actually rolls over, not on every tick.
 *
 * @param secretKey Base32 encoded secret seed, already validated at save time.
 * @param modifier Composable layout modifier.
 * @param totpGenerator Stateless RFC 6238 engine. Defaulted so this composable stays previewable
 * and testable without reaching into DI from the composition.
 */
@Composable
fun TotpCodeField(
    secretKey: String,
    modifier: Modifier = Modifier,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
) {
    val label = stringResource(R.string.totp_code)
    val copiedMessage = stringResource(R.string.copied_to_clipboard, label)
    val copyToClipboard = rememberCopyToClipboardAction()

    val epochSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis() / 1000
        }
    }

    val otpCode = remember(secretKey, epochSeconds / TOTP_PERIOD_SECONDS) {
        totpGenerator.generateCode(secretBase32 = secretKey, timeSeconds = epochSeconds)
    }
    val remainingSeconds = remember(epochSeconds) {
        totpGenerator.getRemainingSeconds(timeSeconds = epochSeconds)
    }
    // grouped as "123 456" purely for readability, the raw code is what gets copied.
    val formattedCode = remember(otpCode) {
        "${otpCode.take(otpCode.length / 2)} ${otpCode.drop(otpCode.length / 2)}"
    }

    Column(
        // TODO: Log AnalyticsKey.AUTHENTICATOR_COPY_CLICK here. Needs an onCopyClick callback
        //  hoisted up to SingleRecordViewModel so the event stays unit testable.
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.cd_copy_totp_code),
            onClick = { copyToClipboard(label, otpCode, copiedMessage) },
        ),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedCode,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_totp_code),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            CircularCountdownRing(
                remainingSeconds = remainingSeconds,
                totalSeconds = TOTP_PERIOD_SECONDS.toInt(),
            )
        }
    }
}

/**
 * RFC 6238 default time step. Kept local to the UI layer because it only drives the countdown ring
 * and the code memoisation key, the engine owns its own default.
 */
private const val TOTP_PERIOD_SECONDS = 30L

@LightDarkModePreview
@Composable
private fun TotpCodeFieldPreview() {
    SafeBoxTheme {
        TotpCodeField(secretKey = "JBSWY3DPEHPK3PXP")
    }
}
