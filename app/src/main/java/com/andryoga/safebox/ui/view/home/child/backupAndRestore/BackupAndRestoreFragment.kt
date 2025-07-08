package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.R
import com.andryoga.safebox.common.DomainMappers.toBackupAndRestoreData
import com.andryoga.safebox.data.db.docs.BackupData
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy.Visibility
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy.VisibilityOff
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@ExperimentalCoroutinesApi
class BackupAndRestoreFragment : Fragment() {
    private val viewModel: BackupAndRestoreViewModel by viewModels()
    private val selectBackupDirReq =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            Timber.i("uri selected for backup = $uri")
            setIsUserAwayTimeoutSuspended(false)
            if (uri != null) {
                Timber.i("path = ${uri.path}")
                val takeFlags: Int =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireActivity().contentResolver.takePersistableUriPermission(
                    uri,
                    takeFlags,
                )
                viewModel.setBackupMetadata(uri)
            }
        }

    @Inject
    lateinit var workManager: WorkManager

    private val selectFileReq =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            Timber.i("uri selected for restore = $uri")
            setIsUserAwayTimeoutSuspended(false)
            if (uri != null) {
                Timber.i("path = ${uri.path}")
                viewModel.selectedFileUriForRestore = uri.toString()
                viewModel.setRestoreScreenState(RestoreScreenState.ENTER_PASSWORD)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        lifecycleScope.launchWhenStarted {
            viewModel.restoreWorkEnqueued.collect {
                if (it != null) {
                    workManager.getWorkInfoByIdLiveData(it)
                        .observe(viewLifecycleOwner) { workInfo ->
                            if (workInfo != null) {
                                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                    viewModel.setRestoreScreenState(RestoreScreenState.COMPLETE)
                                } else if (workInfo.state == WorkInfo.State.FAILED) {
                                    viewModel.setRestoreScreenState(RestoreScreenState.ERROR)
                                }
                            }
                        }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                val backupMetadata by viewModel.backupMetadata.collectAsState(
                    Resource.loading(null),
                )
                BasicSafeBoxTheme {
                    Column(
                        modifier =
                        Modifier
                            .padding(8.dp)
                            .verticalScroll(ScrollState(0)),
                    ) {
                        BackupDataView(backupMetadata)
                        Spacer(modifier = Modifier.height(16.dp))
                        RestoreDataView()
                    }
                }
            }
        }
    }

    @Composable
    private fun BackupDataView(backupMetadataResource: Resource<BackupMetadataEntity?>) {
        val backupScreenState by viewModel.backupScreenState.collectAsState(BackupScreenState.INITIAL_STATE)
        EnterPasswordDialog(
            isVisible =
            backupScreenState in
                listOf(
                    BackupScreenState.ENTER_PASSWORD,
                    BackupScreenState.WRONG_PASSWORD,
                ),
            isPasswordWrong = backupScreenState == BackupScreenState.WRONG_PASSWORD,
            onDismiss = { viewModel.setBackupScreenState(BackupScreenState.INITIAL_STATE) },
            onPasswordSubmit = {
                Timber.i("password submit for backup")
                viewModel.backupData(it)
            },
        )
        Text(
            text = stringResource(R.string.backup),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Medium,
        )
        Divider(color = MaterialTheme.colors.secondary)
        if (backupMetadataResource.status == Status.LOADING) {
            Timber.i("showing loading view")
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (backupMetadataResource.status == Status.SUCCESS) {
            val backupData: BackupData? = backupMetadataResource.data?.toBackupAndRestoreData()
            if (backupData == null) {
                Timber.i("backup path is not set")
                BackupPathNotSetView()
            } else {
                Timber.i("backup path is already set")
                BackupPathSetView(backupData, backupScreenState) {
                    Timber.i("Backup clicked")
                    viewModel.setBackupScreenState(BackupScreenState.ENTER_PASSWORD)
                }
            }
        }
    }

    @Composable
    private fun RestoreDataView() {
        val restoreScreenState by viewModel.restoreScreenState.collectAsState(RestoreScreenState.INITIAL_STATE)
        EnterPasswordDialog(
            isVisible = restoreScreenState == RestoreScreenState.ENTER_PASSWORD,
            isPasswordWrong = false,
            onDismiss = { viewModel.setRestoreScreenState(RestoreScreenState.INITIAL_STATE) },
            onPasswordSubmit = {
                Timber.i("password submit for restore")
                viewModel.restoreData(it)
            },
        )

        when (restoreScreenState) {
            RestoreScreenState.IN_PROGRESS -> {
                RestoreInProgressDialog()
            }

            RestoreScreenState.COMPLETE -> {
                RestoreCompleteDialog()
            }

            RestoreScreenState.ERROR -> {
                RestoreErrorDialog()
            }

            else -> {
                // do nothing
            }
        }

        Column {
            Text(
                text = stringResource(R.string.restore),
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Medium,
            )
            Divider(color = MaterialTheme.colors.secondary)
            Card(
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                elevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.restore_info),
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body1,
                    )
                    Button(
                        onClick = {
                            setIsUserAwayTimeoutSuspended(true)
                            selectFileReq.launch(arrayOf("*/*"))
                        },
                        modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.restore))
                    }
                }
            }
        }
    }

    @Composable
    private fun RestoreErrorDialog() {
        Dialog(onDismissRequest = { viewModel.setRestoreScreenState(RestoreScreenState.INITIAL_STATE) }) {
            Card {
                Row(
                    modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Red,
                    )
                    Text(
                        text = stringResource(R.string.restore_error_message),
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }

    @Composable
    private fun RestoreCompleteDialog() {
        Dialog(onDismissRequest = { viewModel.setRestoreScreenState(RestoreScreenState.INITIAL_STATE) }) {
            Card {
                Row(
                    modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Green,
                    )
                    Text(
                        text = stringResource(R.string.restore_success_message),
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }

    @Composable
    private fun RestoreInProgressDialog() {
        Dialog(
            onDismissRequest = {},
            properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Card {
                Row(
                    modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.restore_in_progress_message),
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }

    @Composable
    fun BackupPathNotSetView() {
        Card(
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            elevation = 2.dp,
            backgroundColor =
            MaterialTheme.colors.error.copy(alpha = 0.05f)
                .compositeOver(MaterialTheme.colors.surface),
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colors.error,
                    modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(64.dp),
                )
                Text(
                    text = stringResource(R.string.backup_path_not_set_message),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.body1,
                )
                Button(
                    onClick = {
                        launchSelectBackupDir()
                    },
                    modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                ) {
                    Text(text = stringResource(R.string.backup_set_location))
                }
            }
        }
    }

    @Composable
    fun BackupPathSetView(
        backupData: BackupData,
        backupScreenState: BackupScreenState,
        onBackupClick: () -> Unit,
    ) {
        Card(
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            elevation = 2.dp,
            backgroundColor = Color.Green.copy(alpha = 0.05f)
                .compositeOver(MaterialTheme.colors.surface),
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.backup_set_message),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.backup_path, backupData.displayPath),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.backup_time, backupData.lastBackupDate),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.backup_info_1),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Thin,
                    color = MaterialTheme.colors.onSurface,
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier =
                    Modifier
                        .padding(bottom = 8.dp, top = 8.dp)
                        .fillMaxWidth(),
                ) {
                    Button(
                        onClick = { launchSelectBackupDir() },
                    ) {
                        Text(text = stringResource(R.string.backup_edit_path))
                    }
                    Button(
                        onClick = { onBackupClick() },
                    ) {
                        Text(text = stringResource(R.string.backup))
                    }
                }
                if (backupScreenState == BackupScreenState.IN_PROGRESS) {
                    Text(
                        text = stringResource(R.string.backup_in_progress_message),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }

    @Composable
    fun EnterPasswordDialog(
        isVisible: Boolean,
        isPasswordWrong: Boolean,
        onDismiss: () -> Unit,
        onPasswordSubmit: (value: String) -> Unit,
    ) {
        if (isVisible) {
            var passwordValue by remember {
                mutableStateOf(
                    TextFieldValue(
                        if (BuildConfig.DEBUG) {
                            "Qwerty@@135"
                        } else {
                            ""
                        },
                    ),
                )
            }
            var isPasswordMasked: Boolean by remember { mutableStateOf(true) }
            Dialog(onDismissRequest = { onDismiss() }) {
                Card(
                    modifier =
                    Modifier
                        .padding(8.dp)
                        .wrapContentHeight(unbounded = true),
                    elevation = 4.dp,
                ) {
                    Column(
                        modifier =
                        Modifier
                            .padding(8.dp),
                    ) {
                        OutlinedTextField(
                            value = passwordValue,
                            onValueChange = {
                                passwordValue = it
                            },
                            modifier =
                            Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            label = { Text(stringResource(R.string.master_password)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation =
                            if (isPasswordMasked) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            trailingIcon = {
                                val image =
                                    if (isPasswordMasked) {
                                        Visibility
                                    } else {
                                        VisibilityOff
                                    }
                                IconButton(
                                    onClick = {
                                        isPasswordMasked = !isPasswordMasked
                                    },
                                ) {
                                    Icon(imageVector = image, null)
                                }
                            },
                        )
                        if (isPasswordWrong) {
                            Text(
                                text = stringResource(R.string.incorrect_pswrd_message),
                                color = MaterialTheme.colors.error,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Button(
                            onClick = { onPasswordSubmit(passwordValue.text) },
                            enabled =
                            passwordValue.text.trim()
                                .isNotEmpty(),
                            modifier =
                            Modifier
                                .align(Alignment.End),
                        ) {
                            Text(text = stringResource(R.string.common_ok))
                        }
                    }
                }
            }
        }
    }

    private fun launchSelectBackupDir() {
        setIsUserAwayTimeoutSuspended(true)
        selectBackupDirReq.launch(null)
    }

    private fun setIsUserAwayTimeoutSuspended(isSuspended: Boolean) {
        (requireActivity() as MainActivity).apply {
            if (isSuspended) {
                suspendUserAwayTimeout()
            } else {
                continueUserAwayTimeout()
            }
        }
    }
}
