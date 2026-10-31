package com.andryoga.safebox.ui.login.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.security.envelope.migration.LegacyVaultMigrator
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationProgressState
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.ui.login.LoginScreenAction
import com.andryoga.safebox.ui.login.MigrationFailureUiState
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Non-dismissable determinate progress card shown while [LegacyVaultMigrator] re-encrypts records.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@Composable
internal fun MigrationProgressCardContent(
    progress: MigrationProgressState.Running,
) {
    val fraction = if (progress.total > 0) {
        (progress.done.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vault_migration_progress_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.vault_migration_progress_counter,
                progress.done,
                progress.total,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Failure state card shown when a legacy vault migration attempt rolls back.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@Composable
internal fun MigrationFailureCardContent(
    failure: MigrationFailureUiState,
    screenAction: (LoginScreenAction) -> Unit,
) {
    LaunchedEffect(failure) {
        screenAction(LoginScreenAction.OnMigrationFailureShown(failure.reason))
    }

    val reasonText = when (failure.reason) {
        MigrationFailureReason.KEYSTORE_TRANSIENT ->
            stringResource(R.string.vault_migration_failure_keystore)

        MigrationFailureReason.VERIFY_MISMATCH ->
            stringResource(R.string.vault_migration_failure_verify)

        MigrationFailureReason.DB_IO ->
            stringResource(R.string.vault_migration_failure_storage)

        MigrationFailureReason.UNEXPECTED,
        MigrationFailureReason.LEGACY_KEY_LOST,
        -> stringResource(R.string.vault_migration_failure_unexpected)
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = reasonText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (failure.trigger == MigrationTrigger.FORGOT_PASSWORD) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vault_migration_failure_forgot_password_note),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { screenAction(LoginScreenAction.OnMigrationRetryClicked) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(stringResource(R.string.vault_migration_retry))
        }
        if (failure.canContinueLegacy) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { screenAction(LoginScreenAction.OnMigrationContinueLegacyClicked) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.vault_migration_continue_legacy))
            }
        }
    }
}

/**
 * Dedicated state shown when the legacy Keystore key (`symmetricDataKey`) is gone and the vault
 * cannot be opened or migrated.
 */
// TODO(MR6): add Erase everything and start over button
// TODO(D3): legacy vault (crypto_version = 1) only
@Composable
internal fun LegacyKeyLostCardContent() {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.legacy_key_lost_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.legacy_key_lost_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One-line notice above the login form, announced to TalkBack when it appears. */
@Composable
internal fun LoginNoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colorScheme.tertiaryContainer,
        contentColor = colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationProgressCardPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationProgressCardContent(
                progress = MigrationProgressState.Running(done = 18, total = 42),
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationProgressCardEmptyVaultPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationProgressCardContent(
                progress = MigrationProgressState.Running(done = 0, total = 0),
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationFailureCardLoginKeystorePreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationFailureCardContent(
                failure = MigrationFailureUiState(
                    reason = MigrationFailureReason.KEYSTORE_TRANSIENT,
                    trigger = MigrationTrigger.LOGIN,
                ),
                screenAction = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationFailureCardLoginVerifyPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationFailureCardContent(
                failure = MigrationFailureUiState(
                    reason = MigrationFailureReason.VERIFY_MISMATCH,
                    trigger = MigrationTrigger.LOGIN,
                ),
                screenAction = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationFailureCardLoginStoragePreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationFailureCardContent(
                failure = MigrationFailureUiState(
                    reason = MigrationFailureReason.DB_IO,
                    trigger = MigrationTrigger.LOGIN,
                ),
                screenAction = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationFailureCardLoginUnexpectedPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationFailureCardContent(
                failure = MigrationFailureUiState(
                    reason = MigrationFailureReason.UNEXPECTED,
                    trigger = MigrationTrigger.LOGIN,
                ),
                screenAction = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun MigrationFailureCardForgotPasswordStoragePreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            MigrationFailureCardContent(
                failure = MigrationFailureUiState(
                    reason = MigrationFailureReason.DB_IO,
                    trigger = MigrationTrigger.FORGOT_PASSWORD,
                ),
                screenAction = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun LegacyKeyLostCardPreview() {
    SafeBoxTheme {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            LegacyKeyLostCardContent()
        }
    }
}

@LightDarkModePreview
@Composable
private fun LoginNoticeBannerUpgradePreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = stringResource(R.string.login_security_upgrade_notice),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkModePreview
@Composable
private fun LoginNoticeBannerAwayTimeoutPreview() {
    SafeBoxTheme {
        LoginNoticeBanner(
            text = stringResource(R.string.login_away_banner),
            modifier = Modifier.padding(16.dp),
        )
    }
}
