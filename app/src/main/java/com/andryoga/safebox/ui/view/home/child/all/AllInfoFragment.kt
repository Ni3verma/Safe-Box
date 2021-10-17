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
import com.andryoga.safebox.NavigationDirections
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.MainActivity
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
        Timber.i("on create view of all info fragment")
        return ComposeView(requireContext()).apply {
            setContent {
                val listData by viewModel.allData.collectAsState(
                    initial = Resource.loading(
                        emptyList()
                    )
                )
                BasicSafeBoxTheme {
                    UserDataList(
                        listResource = listData,
                        onItemClick = { onListItemClick(it) },
                        onDeleteItemClick = { viewModel.onDeleteItemClick(it) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.i("on start of all info fragment")
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).apply {
                setAddNewUserDataVisibility(true)
                setSupportActionBarVisibility(true)
            }
        } else {
            Timber.w("activity expected was MainActivity but was ${requireActivity().localClassName}")
        }
    }

    private fun onListItemClick(item: UserListItemData) {
        Timber.i("clicked ${item.id} - ${item.type.name}")
        findNavController().navigate(
            when (item.type) {
                UserDataType.LOGIN_DATA -> {
                    NavigationDirections.actionGlobalLoginDataFragment(
                        item.id
                    )
                }
                UserDataType.BANK_ACCOUNT -> {
                    NavigationDirections.actionGlobalBankAccountDataFragment(
                        item.id
                    )
                }
                UserDataType.BANK_CARD -> {
                    NavigationDirections.actionGlobalBankCardDataFragment(
                        item.id
                    )
                }
                UserDataType.SECURE_NOTE -> {
                    NavigationDirections.actionGlobalSecureNoteDataFragment(
                        item.id
                    )
                }
            }
        )
    }
}
