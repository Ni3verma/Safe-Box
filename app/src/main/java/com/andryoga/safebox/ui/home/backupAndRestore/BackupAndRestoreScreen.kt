package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.home.backupAndRestore.components.BackupView
import com.andryoga.safebox.ui.home.backupAndRestore.components.RestoreView
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.NewBackupOrRestoreScreen
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.Operation
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestoreScreenRoot() {
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

    val snackbarHostState = remember { SnackbarHostState() }
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
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                launchSelectBackupPath(viewModel, selectBackupPathLauncher)
            }
        }
    }

    Scaffold(
        topBar = {
            MyAppTopAppBar(title = { Text(stringResource(R.string.bottom_nav_backup_and_restore)) })
        },
    ) { innerPadding ->
        BackupAndRestoreScreen(
            uiState = uiState,
            snackBarHostState = snackbarHostState,
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
            },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@VisibleForTesting
@Composable
internal fun BackupAndRestoreScreen(
    uiState: ScreenState,
    modifier: Modifier = Modifier,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    launchRestoreFilePicker: () -> Unit,
    launchSelectBackupPath: () -> Unit,
    onScreenAction: (ScreenAction) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize()
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

        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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