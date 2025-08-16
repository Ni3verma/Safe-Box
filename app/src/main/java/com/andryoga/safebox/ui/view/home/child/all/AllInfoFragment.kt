package com.andryoga.safebox.ui.view.home.child.all

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.common.AnalyticsKeys.DO_NOT_ASK_AGAIN
import com.andryoga.safebox.common.AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK
import com.andryoga.safebox.common.AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK
import com.andryoga.safebox.common.AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_DISMISS
import com.andryoga.safebox.common.AnalyticsKeys.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_SHOWN
import com.andryoga.safebox.common.AnalyticsKeys.PERMISSION_ASKED_BEFORE
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.MainActivity
import com.andryoga.safebox.ui.view.home.child.common.AddNewDataFab
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber

@AndroidEntryPoint
@ExperimentalMaterialApi
@ExperimentalCoroutinesApi
class AllInfoFragment : Fragment() {
    private val viewModel: AllInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("on create view of all info fragment")
        setHasOptionsMenu(true)
        return ComposeView(requireContext()).apply {
            setContent {
                val searchTextFilter by viewModel.searchTextFilter.collectAsState()
                val listData by viewModel.allData.collectAsState(
                    initial = Resource.loading(
                        emptyList()
                    )
                )
                val isBackupPathSet by viewModel.isBackupPathSet.collectAsState()
                var isDisplayDialog by remember { mutableStateOf(true) }

                BasicSafeBoxTheme {
                    Column {
                        // show banner only if backup path is not set and user has some data to backup
                        if (!isBackupPathSet && !listData.data.isNullOrEmpty()) {
                            BackupNotSetBanner()
                        } else if (shouldShowNotificationPermissionRationaleDialog(
                                isBackupPathSet,
                                listData,
                                isDisplayDialog
                            )
                        ) {
                            Firebase.analytics.logEvent(
                                NOTIFICATION_PERMISSION_RATIONALE_DIALOG_SHOWN,
                                null
                            )
                            val permissionResultLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission(),
                                onResult = {}
                            )

                            NotificationPermissionRationaleDialog(
                                onDialogDismiss = {
                                    isDisplayDialog = false
                                    onNotificationPermissionRationaleDialogDismiss()
                                },
                                onAllowClick = {
                                    isDisplayDialog = false
                                    onNotificationPermissionRationaleDialogAllow(
                                        permissionResultLauncher
                                    )
                                },
                                onCancelClick = { doNotAskAgain ->
                                    isDisplayDialog = false
                                    onNotificationPermissionRationaleDialogCancel(doNotAskAgain)
                                }
                            )
                        }
                        UserDataList(
                            listResource = listData,
                            searchTextFilter = searchTextFilter,
                            onItemClick = { onListItemClick(it) },
                            onDeleteItemClick = { viewModel.onDeleteItemClick(it) }
                        )
                    }
                    AddNewDataFab {
                        findNavController()
                            .navigate(R.id.action_nav_all_info_to_addNewUserPersonalDataDialogFragment)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.i("on start of all info fragment")
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).apply {
                setSupportActionBarVisibility(true)
            }
        } else {
            Timber.w("activity expected was MainActivity but was ${requireActivity().localClassName}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_info_screen, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchText(newText)
                return true
            }
        })
    }

    private fun onListItemClick(item: UserListItemData) {
        val id = item.id
        Timber.i("clicked $id - ${item.type.name}")
        findNavController().navigate(
            AllInfoFragmentDirections.actionNavAllInfoToViewDataDetailsFragment(item.type, id)
        )
    }

    @Composable
    fun BackupNotSetBanner() {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colors.error.copy(alpha = 0.05f))
                .padding(8.dp)
                .clickable {
                    Timber.i("backup path not set banner clicked")
                    findNavController().navigate(R.id.action_nav_all_info_to_nav_backup_restore)
                },
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colors.error,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 16.dp)
            )
            Text(
                text = stringResource(id = R.string.backup_not_set_banner_message),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun NotificationPermissionRationaleDialog(
        onDialogDismiss: () -> Unit,
        onAllowClick: () -> Unit,
        onCancelClick: (Boolean) -> Unit
    ) {
        var doNotAskAgain by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = onDialogDismiss,
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(100.dp)
                    )

                    Text(
                        text = stringResource(R.string.notification_permission_rationale_dialog_heading),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.notification_permission_rationale_dialog_body),
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { doNotAskAgain = !doNotAskAgain }
                    ) {
                        Checkbox(
                            checked = doNotAskAgain,
                            onCheckedChange = { isChecked -> doNotAskAgain = isChecked },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                        )
                        Text(
                            text = stringResource(R.string.do_not_ask_again),
                            color = MaterialTheme.colors.secondaryVariant,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCancelClick(doNotAskAgain) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = { onAllowClick() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.allow))
                        }
                    }
                }
            }
        }
    }

    private fun onNotificationPermissionRationaleDialogCancel(doNotAskAgain: Boolean) {
        Timber.i(
            "notification permission dialog cancelled, do not ask again: $doNotAskAgain"
        )
        Firebase.analytics.logEvent(NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK) {
            param(DO_NOT_ASK_AGAIN, doNotAskAgain.toString())
        }
        if (doNotAskAgain) {
            viewModel.isNeverAskForNotificationPermission = true
        }
    }

    private fun onNotificationPermissionRationaleDialogAllow(
        permissionResultLauncher: ManagedActivityResultLauncher<String, Boolean>
    ) {
        Timber.i("notification permission dialog allowed")
        Firebase.analytics.logEvent(NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK) {
            param(PERMISSION_ASKED_BEFORE, viewModel.isNotificationPermissionAskedBefore.toString())
        }
        if (viewModel.isNotificationPermissionAskedBefore) {
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                // user denied last time, we can ask for it again
                permissionResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // user has permanently denied permission, need to give from settings page
                Timber.i("opening settings page for notification permission")
                val intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            requireContext().packageName
                        )
                    }
                startActivity(intent)
            }
        } else {
            // directly ask permission for the first time
            Timber.i("directly asking for permission")
            viewModel.isNotificationPermissionAskedBefore = true
            permissionResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onNotificationPermissionRationaleDialogDismiss() {
        Timber.i("notification permission dialog dismissed")
        Firebase.analytics.logEvent(NOTIFICATION_PERMISSION_RATIONALE_DIALOG_DISMISS, null)
    }

    private fun shouldShowNotificationPermissionRationaleDialog(
        isBackupPathSet: Boolean,
        listData: Resource<List<UserListItemData>>,
        isDisplayDialog: Boolean
    ): Boolean {
        return isBackupPathSet && listData.data.isNullOrEmpty()
            .not() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            isDisplayDialog &&
            viewModel.isNeverAskForNotificationPermission.not()
    }

    @Preview(showBackground = true)
    @Composable
    fun NotificationRationaleDialogPreview() {
        MaterialTheme {
            NotificationPermissionRationaleDialog(
                onDialogDismiss = {},
                onAllowClick = {},
                onCancelClick = {}
            )
        }
    }

    @Preview
    @Composable
    fun BackupNotSetBannerPreview() {
        MaterialTheme {
            BackupNotSetBanner()
        }
    }

//    private fun insertDummyData() {
//        if (BuildConfig.BUILD_TYPE in listOf("debug", "qa")) {
//            viewModel.insertDummyData()
//        }
//    }
}
