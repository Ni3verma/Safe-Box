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
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.DomainMappers.toBackupAndRestoreData
import com.andryoga.safebox.data.db.docs.BackupData
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy.Visibility
import com.andryoga.safebox.ui.common.icons.MaterialIconsCopy.VisibilityOff
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber

@AndroidEntryPoint
@ExperimentalCoroutinesApi
class BackupAndRestoreFragment : Fragment() {
    private val viewModel: BackupAndRestoreViewModel by viewModels()
    private val selectBackupDirReq =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            Timber.i("uri selected for backup = $uri")
            if (uri != null) {
                Timber.i("path = ${uri.path}")
                val takeFlags: Int =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireActivity().contentResolver.takePersistableUriPermission(
                    uri,
                    takeFlags
                )
                viewModel.setBackupMetadata(uri)
            }
        }

    private val selectFileReq =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            Timber.i("uri selected for restore = $uri")
            if (uri != null) {
                Timber.i("path = ${uri.path}")
                viewModel.selectedFileUriForRestore = uri.toString()
                viewModel.setRestoreScreenState(RestoreScreenState.ENTER_PASSWORD)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val backupMetadata by viewModel.backupMetadata.collectAsState(
                    Resource.loading(null)
                )
                BasicSafeBoxTheme {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(ScrollState(0))
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
    private fun BackupDataView(
        backupMetadataResource: Resource<BackupMetadataEntity?>
    ) {
        val backupScreenState by viewModel.backupScreenState.collectAsState(BackupScreenState.INITIAL_STATE)
        EnterPasswordDialog(
            isVisible = backupScreenState == BackupScreenState.ENTER_PASSWORD,
            onDismiss = { viewModel.setBackupScreenState(BackupScreenState.INITIAL_STATE) },
            onPasswordSubmit = {
                Timber.i("password submit for backup")
                viewModel.backupData(it)
            }
        )
        Text(
            text = "Backup",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Medium
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
            onDismiss = { viewModel.setRestoreScreenState(RestoreScreenState.INITIAL_STATE) },
            onPasswordSubmit = {
                Timber.i("password submit for restore")
                viewModel.restoreData(it)
            }
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
        }

        Column {
            Text(
                text = "Restore",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Medium
            )
            Divider(color = MaterialTheme.colors.secondary)
            Card(
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {

                    Text(
                        text = "Click below button to select a file manually. " +
                            "Please note that to restore data, you have to enter the same master password " +
                            "that was used to encrypt the backup file",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body1
                    )
                    Button(
                        onClick = { selectFileReq.launch(arrayOf("application/octet-stream")) },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(text = "Restore")
                    }
                    if (restoreScreenState == RestoreScreenState.WRONG_PASSWORD) {
                        Text(
                            text = "Wrong password entered",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.error,

                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RestoreErrorDialog() {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "TODO",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Red
                    )
                    Text(
                        text = "Error while importing data. Please check that you selected the correct file.",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }

    @Composable
    private fun RestoreCompleteDialog() {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "TODO",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Green
                    )
                    Text(
                        text = "Successfully restored data",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }

    @Composable
    private fun RestoreInProgressDialog() {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "It may take a while to restore data. Sit back and relax !",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface
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
            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.05f)
                .compositeOver(Color.White)
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "TODO",
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(64.dp)
                )
                Text(
                    text = "Backup location is not set. Click below button to set. " +
                        "It is recommended that while selecting location, add a new folder (e.g. Safe Box backup)" +
                        "in local Storage and then select same.",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.body1
                )
                Button(
                    onClick = {
                        selectBackupDirReq.launch(null)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                ) {
                    Text(text = "Set Location")
                }
            }
        }
    }

    @Composable
    fun BackupPathSetView(
        backupData: BackupData,
        backupScreenState: BackupScreenState,
        onBackupClick: () -> Unit
    ) {
        Card(
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            elevation = 2.dp,
            backgroundColor = Color.Green.copy(alpha = 0.05f).compositeOver(Color.White)
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "TODO",
                        tint = Color.Green,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Backup location is set", style = MaterialTheme.typography.h6
                    )
                }
                Text(
                    text = "Backup path is ${backupData.displayPath}",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "Backup was last taken on ${backupData.lastBackupDate}",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "* Backup file is encrypted by your master password",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Thin
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .padding(bottom = 8.dp, top = 8.dp)
                        .fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectBackupDirReq.launch(null) }
                    ) {
                        Text(text = "Edit Path")
                    }
                    Button(
                        onClick = { onBackupClick() }
                    ) {
                        Text(text = "Backup")
                    }
                }
                if (backupScreenState == BackupScreenState.WRONG_PASSWORD) {
                    Text(
                        text = "Wrong password entered",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.error
                    )
                } else if (backupScreenState == BackupScreenState.IN_PROGRESS) {
                    Text(
                        text = "Backup is in-progress",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }

    @Composable
    fun EnterPasswordDialog(
        isVisible: Boolean,
        onDismiss: () -> Unit,
        onPasswordSubmit: (value: String) -> Unit
    ) {
        if (isVisible) {
            var passwordValue by remember {
                mutableStateOf(
                    TextFieldValue(
                        if (BuildConfig.DEBUG) "Qwerty@@135"
                        else ""
                    )
                )
            }
            var isPasswordMasked: Boolean by remember { mutableStateOf(true) }
            Dialog(onDismissRequest = { onDismiss() }) {
                Card(modifier = Modifier.padding(8.dp)) {
                    Column {
                        OutlinedTextField(
                            value = passwordValue,
                            onValueChange = {
                                passwordValue = it
                            },
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            label = { Text("Master Password") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (isPasswordMasked) PasswordVisualTransformation()
                            else VisualTransformation.None,
                            trailingIcon = {
                                val image = if (isPasswordMasked)
                                    Visibility
                                else VisibilityOff
                                IconButton(
                                    onClick = {
                                        isPasswordMasked = !isPasswordMasked
                                    }
                                ) {
                                    Icon(imageVector = image, "TODO")
                                }
                            }
                        )
                        Button(
                            onClick = { onPasswordSubmit(passwordValue.text) },
                            enabled = passwordValue.text.trim()
                                .isNotEmpty(),
                            modifier = Modifier
                                .padding(bottom = 8.dp, end = 16.dp)
                                .align(Alignment.End)
                        ) {
                            Text(text = "OK")
                        }
                    }
                }
            }
        }
    }
}
