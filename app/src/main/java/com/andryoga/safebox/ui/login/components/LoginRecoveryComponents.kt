package com.andryoga.safebox.ui.login.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.login.RecoveryFailureReason
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Login-card state for *Forgot password?* on a `crypto_version = 2` vault that has no usable
 * device wrap: says why nothing can be reset, points at the backup route, and
 * offers *Erase everything and start over*. Setting a lock screen now cannot help — a wrap is
 * only ever created while the vault is open — so no settings shortcut is offered here.
 *
 * @param onEraseVaultClick Opens the erase confirmation.
 * @param onBackClick Returns to the login form (the user may remember the password after all).
 */
@Composable
internal fun RecoveryNotSetUpCardContent(
    onEraseVaultClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.recovery_not_set_up_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recovery_not_set_up_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recovery_not_set_up_restore_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        EraseVaultButton(onClick = onEraseVaultClick)
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.recovery_back_to_login))
        }
    }
}

/** The destructive entry point shared by the recovery-not-set-up and legacy-key-lost cards. */
@Composable
internal fun EraseVaultButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.error,
            contentColor = colorScheme.onError,
        ),
    ) {
        Text(stringResource(R.string.erase_vault_action))
    }
}

/** Copy for the recovery-failure banner above the login form. */
@Composable
internal fun recoveryFailureText(reason: RecoveryFailureReason): String = when (reason) {
    RecoveryFailureReason.NOT_AUTHENTICATED -> stringResource(R.string.recovery_failure_not_authenticated)
    RecoveryFailureReason.VERIFIER -> stringResource(R.string.recovery_failure_verifier)
    RecoveryFailureReason.KEYSTORE -> stringResource(R.string.recovery_failure_keystore)
    RecoveryFailureReason.STORAGE -> stringResource(R.string.recovery_failure_storage)
}

@LightDarkModePreview
@Composable
private fun RecoveryNotSetUpCardPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            RecoveryNotSetUpCardContent(
                onEraseVaultClick = {},
                onBackClick = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun RecoveryFailureBannerVerifierPreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = recoveryFailureText(RecoveryFailureReason.VERIFIER),
            isError = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecoveryFailureBannerNotAuthenticatedPreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = recoveryFailureText(RecoveryFailureReason.NOT_AUTHENTICATED),
            isError = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecoveryFailureBannerKeystorePreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = recoveryFailureText(RecoveryFailureReason.KEYSTORE),
            isError = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecoveryFailureBannerStoragePreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = recoveryFailureText(RecoveryFailureReason.STORAGE),
            isError = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun BiometricInvalidatedBannerPreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = stringResource(R.string.login_biometric_invalidated_banner),
            modifier = Modifier.padding(16.dp),
        )
    }
}
