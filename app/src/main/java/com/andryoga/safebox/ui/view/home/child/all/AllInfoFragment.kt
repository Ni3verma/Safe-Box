package com.andryoga.safebox.ui.view.home.child.all

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.MainActivity
import com.andryoga.safebox.ui.view.home.child.common.AddNewDataFab
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
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
        savedInstanceState: Bundle?,
    ): View {
        Timber.i("on create view of all info fragment")
        setHasOptionsMenu(true)
        return ComposeView(requireContext()).apply {
            setContent {
                val searchTextFilter by viewModel.searchTextFilter.collectAsState()
                val listData by viewModel.allData.collectAsState(
                    initial =
                        Resource.loading(
                            emptyList(),
                        ),
                )
                val isBackupPathSet by viewModel.isBackupPathSet.collectAsState()
                BasicSafeBoxTheme {
                    Column {
                        // show banner only if backup path is not set and user has some data to backup
                        if (!isBackupPathSet && !listData.data.isNullOrEmpty()) {
                            BackupNotSetBanner()
                        }
                        UserDataList(
                            listResource = listData,
                            searchTextFilter = searchTextFilter,
                            onItemClick = { onListItemClick(it) },
                            onDeleteItemClick = { viewModel.onDeleteItemClick(it) },
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

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.home_info_screen, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView

        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.setSearchText(newText)
                    return true
                }
            },
        )
    }

    private fun onListItemClick(item: UserListItemData) {
        val id = item.id
        Timber.i("clicked $id - ${item.type.name}")
        findNavController().navigate(
            AllInfoFragmentDirections.actionNavAllInfoToViewDataDetailsFragment(item.type, id),
        )
    }

    @Composable
    fun BackupNotSetBanner() {
        Row(
            modifier =
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.error.copy(alpha = 0.05f))
                    .padding(8.dp)
                    .clickable {
                        Timber.i("backup path not set banner clicked")
                        findNavController().navigate(R.id.action_nav_all_info_to_nav_backup_restore)
                    },
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colors.error,
                modifier =
                    Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 16.dp),
            )
            Text(
                text = stringResource(id = R.string.backup_not_set_banner_message),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center,
            )
        }
    }

//    private fun insertDummyData() {
//        if (BuildConfig.BUILD_TYPE in listOf("debug", "qa")) {
//            viewModel.insertDummyData()
//        }
//    }
}
