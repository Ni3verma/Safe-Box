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
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.core.CircularCountdownRing
import com.andryoga.safebox.ui.core.rememberCopyToClipboardAction
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.totp.rememberTotpCodeState

/**
 * View mode field rendering the live rolling 2FA code derived from a stored Base32 seed.
 *
 * Styled like every other read only field in `SingleRecordScreen` (label above value) so the
 * authenticator record does not visually break the field list, with the value replaced by the
 * rolling code plus its countdown ring.
 *
 * @param config Seed plus the record's own generation parameters, validated at save time.
 * @param modifier Composable layout modifier.
 * @param totpGenerator Stateless RFC 6238 engine. Defaulted so this composable stays previewable
 * and testable without reaching into DI from the composition.
 */
@Composable
fun TotpCodeField(
    config: TotpConfig,
    modifier: Modifier = Modifier,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
) {
    val label = stringResource(R.string.totp_code)
    val copiedMessage = stringResource(R.string.copied_to_clipboard, label)
    val copyToClipboard = rememberCopyToClipboardAction()

    // restore-from-backup can insert an unvalidated seed, so degrade to an inline error.
    val totpCodeState = rememberTotpCodeState(config = config, totpGenerator = totpGenerator)
    if (totpCodeState == null) {
        InvalidSecretKeyField(label = label, modifier = modifier)
        return
    }

    Column(
        // TODO: Log AnalyticsKey.AUTHENTICATOR_COPY_CLICK here. Needs an onCopyClick callback
        //  hoisted up to SingleRecordViewModel so the event stays unit testable.
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.cd_copy_totp_code),
            onClick = { copyToClipboard(label, totpCodeState.code, copiedMessage) },
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
                    text = totpCodeState.formattedCode,
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
                remainingSeconds = totpCodeState.remainingSeconds,
                totalSeconds = config.period,
            )
        }
    }
}

/**
 * Fallback shown when the stored seed cannot produce a code.
 *
 * Keeps the record openable so the user can still read the title and repair or delete the entry,
 * rather than the screen crashing on a corrupt seed.
 *
 * @param label Field label, kept identical to the healthy state for visual consistency.
 * @param modifier Composable layout modifier.
 */
@Composable
private fun InvalidSecretKeyField(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.totp_invalid_secret_key),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun TotpCodeFieldPreview() {
    SafeBoxTheme {
        TotpCodeField(config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP"))
    }
}

@LightDarkModePreview
@Composable
private fun TotpCodeFieldEightDigitCodePreview() {
    SafeBoxTheme {
        TotpCodeField(config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP", digits = 8))
    }
}

@LightDarkModePreview
@Composable
private fun TotpCodeFieldInvalidSecretPreview() {
    SafeBoxTheme {
        TotpCodeField(config = TotpConfig(secretKey = "not-a-valid-base32-seed!"))
    }
}
