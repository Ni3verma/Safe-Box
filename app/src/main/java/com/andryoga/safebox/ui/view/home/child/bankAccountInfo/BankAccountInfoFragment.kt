package com.andryoga.safebox.ui.view.home.child.bankAccountInfo

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.home.child.common.AddNewDataFab
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
@ExperimentalMaterialApi
class BankAccountInfoFragment : Fragment() {
    private val viewModel: BankAccountInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        setHasOptionsMenu(true)
        return ComposeView(requireContext()).apply {
            setContent {
                val searchTextFilter by viewModel.searchTextFilter.collectAsState()
                val listData by viewModel.listData.collectAsState(
                    initial =
                    Resource.loading(
                        emptyList(),
                    ),
                )
                BasicSafeBoxTheme {
                    UserDataList(
                        listResource = listData,
                        searchTextFilter = searchTextFilter,
                        onItemClick = { onListItemClick(it) },
                        onDeleteItemClick = { viewModel.onDeleteItemClick(it) },
                    )
                    AddNewDataFab {
                        findNavController()
                            .navigate(R.id.action_nav_bank_account_info_to_addNewUserPersonalDataDialogFragment)
                    }
                }
            }
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
        Timber.i("clicked ${item.id}")
        findNavController().navigate(
            BankAccountInfoFragmentDirections.actionNavBankAccountInfoToViewDataDetailsFragment(item.type, item.id),
        )
    }
}
