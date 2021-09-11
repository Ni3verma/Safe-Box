package com.andryoga.safebox.ui.view.home.child.all

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.home.HomeFragmentDirections
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
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
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val listData by viewModel.allData.collectAsState(
                    initial = Resource.loading(
                        emptyList()
                    )
                )
                BasicSafeBoxTheme {
                    UserDataList(
                        listResource = listData
                    ) {
                        onListItemClick(it)
                    }
                }
            }
        }
    }

    private fun onListItemClick(item: UserListItemData) {
        // first parent is NavHostFragment, then we get parent of it to get home fragment
        val parent = requireParentFragment().requireParentFragment()
        Timber.i("clicked ${item.id} - ${item.type.name}")
        parent.findNavController().navigate(
            when (item.type) {
                UserDataType.LOGIN_DATA -> {
                    HomeFragmentDirections.actionHomeFragmentToLoginDataFragment(
                        item.id
                    )
                }
                UserDataType.BANK_ACCOUNT -> {
                    HomeFragmentDirections.actionHomeFragmentToBankAccountDataFragment(
                        item.id
                    )
                }
                UserDataType.BANK_CARD -> {
                    HomeFragmentDirections.actionHomeFragmentToBankCardDataFragment(
                        item.id
                    )
                }
                UserDataType.SECURE_NOTE -> {
                    HomeFragmentDirections.actionHomeFragmentToSecureNoteDataFragment(
                        item.id
                    )
                }
            }
        )
    }
}
