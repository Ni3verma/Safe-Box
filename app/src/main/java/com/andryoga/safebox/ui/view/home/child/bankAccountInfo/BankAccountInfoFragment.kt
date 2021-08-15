package com.andryoga.safebox.ui.view.home.child.bankAccountInfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BankAccountInfoFragment : Fragment() {
    private val viewModel: BankAccountInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {

            setContent {
                val listData by viewModel.listData.collectAsState(
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
        Timber.i("clicked ${item.id}")
        CommonSnackbar.showSuccessSnackbar(
            requireView(),
            "FUTURE FEATURE : ${item.id}"
        )
    }
}
