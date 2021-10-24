package com.andryoga.safebox.ui.view.home.child.secureNoteInfo

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
class SecureNoteInfoFragment : Fragment() {
    private val viewModel: SecureNoteViewModel by viewModels()

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
                        listResource = listData,
                        onItemClick = { onListItemClick(it) },
                        onDeleteItemClick = { viewModel.onDeleteItemClick(it) }
                    )
                    AddNewDataFab() {
                        findNavController()
                            .navigate(R.id.action_nav_secure_note_info_to_addNewUserPersonalDataDialogFragment)
                    }
                }
            }
        }
    }

    private fun onListItemClick(item: UserListItemData) {
        Timber.i("clicked ${item.id}")
        findNavController().navigate(
            SecureNoteInfoFragmentDirections.actionNavSecureNoteInfoToSecureNoteDataFragment(item.id)
        )
    }
}
