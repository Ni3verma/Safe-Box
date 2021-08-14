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
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.andryoga.safebox.ui.view.home.child.common.UserDataList
import dagger.hilt.android.AndroidEntryPoint

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
                val listData by viewModel.listData.collectAsState(initial = listOf())
                BasicSafeBoxTheme {
                    UserDataList(list = listData)
                }
            }
        }
    }
}
