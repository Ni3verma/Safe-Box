package com.andryoga.composeapp.ui.home.backupAndRestore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.home.backupAndRestore.BackupNotSet
import com.andryoga.composeapp.ui.home.backupAndRestore.BackupSet
import com.andryoga.composeapp.ui.home.backupAndRestore.Loading
import com.andryoga.composeapp.ui.home.backupAndRestore.ScreenAction
import com.andryoga.composeapp.ui.home.backupAndRestore.ScreenState
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.theme.SafeBoxTheme


@Composable
fun BackupView(
    uiState: ScreenState,
    onScreenAction: (ScreenAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.backup),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider()

        when (uiState.backupState) {
            is Loading -> BackupPathLoading()
            is BackupNotSet -> BackupPathNotSet(onScreenAction = onScreenAction)
            is BackupSet -> BackupPathSet(
                backupState = uiState.backupState,
                onScreenAction = onScreenAction
            )
        }

    }
}

@Composable
private fun BackupPathLoading() {
    Card(
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Box(
            // Use Box and specify the alignment for its content
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth() // Important: Make the Box fill the Card's bounds
                .padding(16.dp)
        )
        { CircularProgressIndicator() }
    }
}

@Composable
private fun BackupPathNotSet(onScreenAction: (ScreenAction) -> Unit) {
    Card(
        modifier = Modifier.padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                .compositeOver(MaterialTheme.colorScheme.surface)
        ),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(64.dp)
            )
            Text(
                text = stringResource(R.string.backup_path_not_set_message),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = {},
            ) {
                Text(text = stringResource(R.string.backup_set_location))
            }
        }
    }
}

@Composable
private fun BackupPathSet(backupState: BackupSet, onScreenAction: (ScreenAction) -> Unit) {
    Card(
        modifier = Modifier.padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Green.copy(alpha = 0.05f)
                .compositeOver(MaterialTheme.colorScheme.surface)
        ),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = stringResource(R.string.backup_set_message),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.backup_path, backupState.backupPath),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.backup_time, backupState.backupTime),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.backup_info_1),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .padding(bottom = 8.dp, top = 8.dp)
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = { }
                ) {
                    Text(text = stringResource(R.string.backup_edit_path))
                }
                Button(
                    onClick = { }
                ) {
                    Text(text = stringResource(R.string.backup))
                }
            }
            // if backup is in progress
            if (true) {
                Text(
                    text = stringResource(R.string.backup_in_progress_message),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Thin
                )
            }
        }
    }
}

@LightDarkModePreview
@Composable
private fun BackupScreenLoadingPreview() {
    SafeBoxTheme {
        BackupView(
            uiState = ScreenState(),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun BackupScreenPathSetPreview() {
    SafeBoxTheme {
        BackupView(
            uiState = ScreenState(
                backupState = BackupSet(
                    backupPath = "/tree/primary:Safebox debug",
                    backupTime = "28 Sep 2025 05:04 PM"
                )
            ),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun BackupScreenPathNotSetPreview() {
    SafeBoxTheme {
        BackupView(
            uiState = ScreenState(backupState = BackupNotSet()),
            onScreenAction = {}
        )
    }
}
