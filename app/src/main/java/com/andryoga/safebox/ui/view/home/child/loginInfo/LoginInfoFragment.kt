package com.andryoga.safebox.ui.view.home.child.loginInfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.LoginInfoFragmentBinding
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapter
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapterEntity
import com.andryoga.safebox.ui.view.home.child.common.UserDataClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import timber.log.Timber

@AndroidEntryPoint
class LoginInfoFragment : Fragment() {
    private val viewModel: LoginInfoViewModel by viewModels()

    private lateinit var binding: LoginInfoFragmentBinding
    private lateinit var adapter: UserDataAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.login_info_fragment, container, false)
        binding.lifecycleOwner = this

        adapter = UserDataAdapter(
            UserDataClickListener { userDataAdapterEntity ->
                handleAdapterItemClick(userDataAdapterEntity)
            }
        )

        binding.loginInfoRv.adapter = adapter
        lifecycleScope.launchWhenStarted {
            viewModel.listData.collect { data ->
                if (data.isEmpty()) {
                    binding.emptyView.isVisible = true
                    binding.emptyView.emptyViewBackground.setImageResource(R.drawable.no_result)
                } else {
                    binding.emptyView.isVisible = false
                    binding.emptyView.emptyViewBackground.setImageResource(R.drawable.empty)
                }
                adapter.submitList(data)
            }
        }
        return binding.root
    }

    private fun handleAdapterItemClick(userDataAdapterEntity: UserDataAdapterEntity) {
        Timber.i("clicked ${userDataAdapterEntity.id}")
        CommonSnackbar.showSuccessSnackbar(
            requireView(),
            "FUTURE FEATURE : ${userDataAdapterEntity.id}"
        )
    }
}
