package com.andryoga.safebox.ui.home.records.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.core.CircularCountdownRing
import com.andryoga.safebox.ui.core.rememberCopyToClipboardAction
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.totp.rememberTotpCodeState

/**
 * Compact live one-time code shown on an authenticator row in the records list.
 *
 * @param config Seed plus the record's own generation parameters.
 * @param onCopyClick Invoked after the code is copied, so the caller can log analytics. Mandatory
 * so a new call site cannot silently drop the event.
 * @param modifier Composable layout modifier.
 * @param totpGenerator Stateless RFC 6238 engine. Defaulted so this composable stays previewable
 * and testable without reaching into DI from the composition.
 */
@Composable
fun TotpBadge(
    config: TotpConfig,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
) {
    val label = stringResource(R.string.totp_code)
    val copiedMessage = stringResource(R.string.copied_to_clipboard, label)
    val copyToClipboard = rememberCopyToClipboardAction()

    val totpCodeState = rememberTotpCodeState(config = config, totpGenerator = totpGenerator)
    if (totpCodeState == null) {
        Text(
            text = stringResource(R.string.totp_invalid_secret_key),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularCountdownRing(
            remainingSeconds = totpCodeState.remainingSeconds,
            totalSeconds = config.period,
            size = 24.dp,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = totpCodeState.formattedCode,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(
            onClick = {
                copyToClipboard(label, totpCodeState.code, copiedMessage)
                onCopyClick()
            },
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_totp_code),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview
@Composable
private fun TotpBadgePreview() {
    SafeBoxTheme {
        TotpBadge(config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP"), onCopyClick = {})
    }
}

@Preview
@Composable
private fun TotpBadgeEightDigitCodePreview() {
    SafeBoxTheme {
        TotpBadge(
            config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP", digits = 8),
            onCopyClick = {},
        )
    }
}

@Preview
@Composable
private fun TotpBadgeInvalidSecretPreview() {
    SafeBoxTheme {
        TotpBadge(config = TotpConfig(secretKey = "not-a-valid-base32-seed!"), onCopyClick = {})
    }
}
