package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.home.backupAndRestore.components.BackupView
import com.andryoga.safebox.ui.home.backupAndRestore.components.RestoreView
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.NewBackupOrRestoreScreen
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.Operation
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.OnStart
import timber.log.Timber

@Composable
fun BackupAndRestoreScreenRoot(mainViewModel: MainViewModel) {
    val viewModel = hiltViewModel<BackupAndRestoreVM>()
    val uiState by viewModel.uiState.collectAsState()

    val selectRestoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            viewModel.resumeActiveSessionManager()
            Timber.i("uri selected for restore = $uri")
            viewModel.onScreenAction(ScreenAction.RestoreFileSelected(uri))
        }
    )

    val selectBackupPathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            viewModel.resumeActiveSessionManager()
            Timber.i("uri selected for backup = $uri")
            viewModel.onScreenAction(ScreenAction.BackupPathSelected(uri))
        }
    )

    OnStart {
        val config = TopAppBarConfig(
            title = { Text(stringResource(R.string.bottom_nav_backup_and_restore)) },
        )
        mainViewModel.updateTopBar(config)
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val errorMessage = stringResource(R.string.backup_path_select_failed_message)
    val retryLabel = stringResource(R.string.retry)

    LaunchedEffect(Unit) {
        viewModel.startRestoreWorkflow.collect {
            launchRestorePicker(viewModel, selectRestoreFileLauncher)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.showBackupPathPermissionError.collect {
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = retryLabel,
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                launchSelectBackupPath(viewModel, selectBackupPathLauncher)
            }
        }
    }

    BackupAndRestoreScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        launchRestoreFilePicker = {
            Timber.i("launching restore file picker")
            launchRestorePicker(viewModel, selectRestoreFileLauncher)
        },
        launchSelectBackupPath = {
            Timber.i("launching select backup path")
            launchSelectBackupPath(viewModel, selectBackupPathLauncher)
        },
        onScreenAction = { action ->
            viewModel.onScreenAction(action)
        }
    )
}

@androidx.annotation.VisibleForTesting
@Composable
internal fun BackupAndRestoreScreen(
    uiState: ScreenState,
    snackbarHostState: androidx.compose.material3.SnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() },
    launchRestoreFilePicker: () -> Unit,
    launchSelectBackupPath: () -> Unit,
    onScreenAction: (ScreenAction) -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            BackupView(
                backupState = uiState.backupState,
                launchSelectBackupPath = launchSelectBackupPath,
                onScreenAction = onScreenAction
            )
            RestoreView(
                launchRestoreFilePicker = launchRestoreFilePicker
            )
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
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
            launchRestoreFilePicker = {},
            launchSelectBackupPath = {},
        ) {}
    }
}

@LightDarkModePreview
@Composable
private fun BackupAndRestorePathNotSetPreview() {
    SafeBoxTheme {
        BackupAndRestoreScreen(
            uiState = ScreenState(backupState = BackupPathNotSet()),
            launchRestoreFilePicker = {},
            launchSelectBackupPath = {},
        ) {}
    }
}

private fun launchSelectBackupPath(
    viewModel: BackupAndRestoreVM,
    selectBackupPathLauncher: ManagedActivityResultLauncher<Uri?, Uri?>
) {
    viewModel.pauseActiveSessionManager()
    selectBackupPathLauncher.launch(null)
}

fun launchRestorePicker(
    viewModel: BackupAndRestoreVM,
    selectRestoreFileLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>
) {
    viewModel.pauseActiveSessionManager()
    val backupMimeTypes = arrayOf(
        "application/octet-stream",
        "application/x-trash",
        "application/x-binary"
    )
    selectRestoreFileLauncher.launch(backupMimeTypes)
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
            launchRestoreFilePicker = {},
            launchSelectBackupPath = {},
        ) {}
    }
}