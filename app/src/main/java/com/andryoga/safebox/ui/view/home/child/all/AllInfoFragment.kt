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
import com.andryoga.safebox.ui.view.MainActivity
import com.andryoga.safebox.ui.view.home.child.bankAccountInfo.BankAccountInfoFragmentDirections
import com.andryoga.safebox.ui.view.home.child.bankCardInfo.BankCardInfoFragmentDirections
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import com.andryoga.safebox.ui.view.home.child.loginInfo.LoginInfoFragmentDirections
import com.andryoga.safebox.ui.view.home.child.secureNoteInfo.SecureNoteInfoFragmentDirections
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
        val id = item.id
        Timber.i("clicked $id - ${item.type.name}")
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).apply {
                setAddNewUserDataVisibility(false)
            }
        } else {
            Timber.w("activity expected was MainActivity but was ${requireActivity().localClassName}")
        }

        findNavController().navigate(
            when (item.type) {
                UserDataType.LOGIN_DATA -> {
                    LoginInfoFragmentDirections.actionNavLoginInfoToLoginDataFragment(id)
                }
                UserDataType.BANK_ACCOUNT -> {
                    BankAccountInfoFragmentDirections.actionNavBankAccountInfoToBankAccountDataFragment(id)
                }
                UserDataType.BANK_CARD -> {
                    BankCardInfoFragmentDirections.actionNavBankCardInfoToBankCardDataFragment(id)
                }
                UserDataType.SECURE_NOTE -> {
                    SecureNoteInfoFragmentDirections.actionNavSecureNoteInfoToSecureNoteDataFragment(id)
                }
            }
        )
    }

//    private fun insertDummyData() {
//        if (BuildConfig.BUILD_TYPE in listOf("debug", "qa")) {
//            viewModel.insertDummyData()
//        }
//    }
}
