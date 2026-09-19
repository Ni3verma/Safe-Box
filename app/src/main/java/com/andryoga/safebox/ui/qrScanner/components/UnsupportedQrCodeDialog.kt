package com.andryoga.safebox.ui.qrScanner.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Dialog explaining why a scanned `otpauth://` QR code cannot be turned into a record.
 *
 * @param reason Which part of the scanned URI Safe-Box rejected.
 * @param onDismiss Callback invoked to close the dialog and resume scanning.
 */
@Composable
fun UnsupportedQrCodeDialog(
    reason: TotpUriError,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        UnsupportedQrCodeDialogContent(
            reason = reason,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Card body of [UnsupportedQrCodeDialog], separated from the [Dialog] wrapper so it can be
 * rendered by a `@Preview`. Layoutlib composes into a single view hierarchy and cannot draw the
 * separate window a `Dialog` creates, so previewing the wrapper shows nothing.
 *
 * @param reason Which part of the scanned URI Safe-Box rejected.
 * @param onDismiss Callback invoked to close the dialog and resume scanning.
 */
@Composable
private fun UnsupportedQrCodeDialogContent(
    reason: TotpUriError,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.unsupported_qr_code_dialog_heading),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(reason.messageResId()),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.common_ok))
            }
        }
    }
}

/**
 * Picks the explanation shown for a rejection reason.
 *
 * @return String resource describing what Safe-Box could not accept and what it supports instead.
 */
@StringRes
private fun TotpUriError.messageResId(): Int = when (this) {
    TotpUriError.MALFORMED_URI -> R.string.unsupported_qr_code_reason_malformed
    TotpUriError.UNSUPPORTED_OTP_TYPE -> R.string.unsupported_qr_code_reason_otp_type
    TotpUriError.INVALID_SECRET -> R.string.unsupported_qr_code_reason_invalid_secret
    TotpUriError.UNSUPPORTED_ALGORITHM -> R.string.unsupported_qr_code_reason_algorithm
    TotpUriError.UNSUPPORTED_DIGITS -> R.string.unsupported_qr_code_reason_digits
    TotpUriError.UNSUPPORTED_PERIOD -> R.string.unsupported_qr_code_reason_period
}

@LightDarkModePreview
@Composable
private fun UnsupportedQrCodeDialogContentPreview() {
    SafeBoxTheme {
        UnsupportedQrCodeDialogContent(
            reason = TotpUriError.UNSUPPORTED_ALGORITHM,
            onDismiss = {},
        )
    }
}
