package com.andryoga.composeapp.ui.home.backupAndRestore

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.MainViewModel
import com.andryoga.composeapp.ui.core.TopAppBarConfig
import com.andryoga.composeapp.ui.home.backupAndRestore.components.BackupView
import com.andryoga.composeapp.ui.home.backupAndRestore.components.RestoreView
import com.andryoga.composeapp.ui.home.backupAndRestore.components.newBackupOrRestore.NewBackupOrRestoreScreen
import com.andryoga.composeapp.ui.home.backupAndRestore.components.newBackupOrRestore.Operation
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import com.andryoga.composeapp.ui.utils.OnStart
import timber.log.Timber

@Composable
fun BackupAndRestoreScreenRoot(mainViewModel: MainViewModel) {
    val viewModel = hiltViewModel<BackupAndRestoreVM>()
    val uiState by viewModel.uiState.collectAsState()
    OnStart {
        val config = TopAppBarConfig(
            title = { Text(stringResource(R.string.bottom_nav_backup_and_restore)) },
        )
        mainViewModel.updateTopBar(config)
    }

    BackupAndRestoreScreen(
        uiState = uiState,
        onScreenAction = { action ->
            viewModel.onScreenAction(action)
        }
    )
}

@Composable
private fun BackupAndRestoreScreen(
    uiState: ScreenState,
    onScreenAction: (ScreenAction) -> Unit,
) {
    Column {
        BackupView(
            backupState = uiState.backupState,
            onScreenAction = onScreenAction
        )
        RestoreView(
            onRestoreFileSelected = { uri ->
                onScreenAction(ScreenAction.RestoreFileSelected(uri))
            }
        )
    }

    when (uiState.newBackupOrRestoreScreenState) {
        NewBackupOrRestoreScreenState.NotStarted -> null
        NewBackupOrRestoreScreenState.StartedForBackup -> Operation.Backup
        is NewBackupOrRestoreScreenState.StartedForRestore -> Operation.Restore(fileUri = uiState.newBackupOrRestoreScreenState.fileUri)
    }?.let { operation ->
        Timber.i("launching new backup or restore dialog, operation = $operation")
        NewBackupOrRestoreScreen(
            operation = operation,
            onDismiss = {
                onScreenAction(ScreenAction.NewBackupOrRestoreDismiss)
            }
        )
    }
}

@LightDarkModePreview
@Composable
private fun BackupAndRestorePathLoadingPreview() {
    SafeBoxTheme {
        BackupAndRestoreScreen(
            uiState = ScreenState(),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun BackupAndRestorePathNotSetPreview() {
    SafeBoxTheme {
        BackupAndRestoreScreen(
            uiState = ScreenState(backupState = BackupPathNotSet()),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun BackupAndRestorePathSetPreview() {
    SafeBoxTheme {
        BackupAndRestoreScreen(
            uiState = ScreenState(
                backupState = BackupPathSet(
                    backupPath = "/tree/primary:Safebox debug",
                    backupTime = "28 Sep 2025 05:04 PM"
                )
            ),
            onScreenAction = {}
        )
    }
}