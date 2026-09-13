package com.andryoga.safebox.ui.singleRecord.components

import android.content.ClipData
import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.ui.core.CircularCountdownRing
import com.andryoga.safebox.ui.core.LocalSnackbarHostState
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Display card for live 2FA TOTP rolling verification codes in Single Record view mode.
 *
 * Runs an internal 1-second ticker using [produceState] to recompute the remaining countdown
 * and code without triggering full screen recomposition. Offers a single-tap copy action
 * to copy the unspaced 6-digit code to the system clipboard.
 *
 * @param secretKey RFC 4648 Base32 encoded secret key.
 * @param modifier Composable layout modifier.
 * @param totpGenerator Engine for evaluating RFC 6238 TOTP codes and remaining time.
 * @param onCopySuccess Optional callback invoked when the code is copied to clipboard.
 */
@Composable
fun TotpCodeDisplayCard(
    secretKey: String,
    modifier: Modifier = Modifier,
    totpGenerator: TotpGenerator = remember { TotpGeneratorImpl() },
    onCopySuccess: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackBarHost = LocalSnackbarHostState.current
    val copiedMessage = stringResource(R.string.copied_code_to_clipboard)

    val epochSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis() / 1000
        }
    }

    val isValidSecret = remember(secretKey) {
        totpGenerator.isValidSecret(secretKey)
    }

    val otpCode = remember(secretKey, epochSeconds / 30, isValidSecret) {
        if (isValidSecret) {
            totpGenerator.generateCode(
                secretBase32 = secretKey,
                timeSeconds = epochSeconds,
            )
        } else {
            "------"
        }
    }

    val remainingSeconds = remember(epochSeconds) {
        totpGenerator.getRemainingSeconds(timeSeconds = epochSeconds)
    }

    val formattedCode = remember(otpCode) {
        if (otpCode.length == 6) {
            "${otpCode.take(3)} ${otpCode.drop(3)}"
        } else {
            otpCode
        }
    }

    val copyCodeAction: () -> Unit = {
        if (isValidSecret && otpCode != "------") {
            scope.launch {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText(
                            "One-time code",
                            otpCode,
                        ),
                    ),
                )

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackBarHost.currentSnackbarData?.dismiss()
                    snackBarHost.showSnackbar(
                        message = copiedMessage,
                        duration = SnackbarDuration.Short,
                    )
                }
                onCopySuccess?.invoke()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.cd_copy_totp_code),
                onClick = copyCodeAction,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.totp_code),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularCountdownRing(
                        remainingSeconds = remainingSeconds,
                        totalSeconds = 30,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = copyCodeAction,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_totp_code),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@LightDarkModePreview
@Composable
private fun TotpCodeDisplayCardPreview() {
    SafeBoxTheme {
        TotpCodeDisplayCard(
            secretKey = "JBSWY3DPEHPK3PXP",
        )
    }
}
