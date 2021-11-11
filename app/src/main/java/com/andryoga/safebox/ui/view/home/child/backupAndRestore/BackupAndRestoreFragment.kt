package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.andryoga.safebox.common.DomainMappers.toBackupAndRestoreData
import com.andryoga.safebox.data.db.docs.BackupData
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BackupAndRestoreFragment : Fragment() {
    private val viewModel: BackupAndRestoreViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val backupMetadata: BackupMetadataEntity? by viewModel.backupMetadata.collectAsState(
                    initial = null
                )
                val backupData: BackupData? = backupMetadata?.toBackupAndRestoreData()
                BasicSafeBoxTheme {
                    Column(modifier = Modifier.padding(8.dp)) {
                        BackupDataView(backupData)
                        Spacer(modifier = Modifier.height(16.dp))
                        RestoreDataView()
                    }
                }
            }
        }
    }

    @Composable
    private fun BackupDataView(backupData: BackupData?) {
        Text(
            text = "Backup",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Medium
        )
        Divider(color = MaterialTheme.colors.secondary)
        if (backupData == null) {
            Timber.i("backup path is not set")
            BackupPathNotSetView()
        } else {
            Timber.i("backup path is already set")
            BackupPathSetView(backupData)
        }
    }

    @Composable
    private fun RestoreDataView() {
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
                    modifier = Modifier.padding(top = 4.dp)
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
                        onClick = { /*TODO*/ },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(text = "Restore")
                    }
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
                        " and then select same.",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.body1
                )
                Button(
                    onClick = { /*TODO*/ },
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
    fun BackupPathSetView(backupData: BackupData) {
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
                        text = "Backup location is set.", style = MaterialTheme.typography.h6
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
                    text = "* Backup file is encrypted by your master password\n" +
                        "* It is good practice to store the backup file in your PC/GDrive/Dropbox/etc",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Thin
                )

                Button(
                    onClick = { /*TODO*/ },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                ) {
                    Text(text = "Backup")
                }
            }
        }
    }
}
