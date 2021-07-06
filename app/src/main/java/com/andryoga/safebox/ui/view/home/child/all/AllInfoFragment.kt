package com.andryoga.safebox.ui.view.home.child.all

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.AllInfoFragmentBinding
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapter
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapterEntity
import com.andryoga.safebox.ui.view.home.child.common.UserDataClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import timber.log.Timber

@AndroidEntryPoint
class AllInfoFragment : Fragment() {
    private val viewModel: AllInfoViewModel by viewModels()

    private lateinit var binding: AllInfoFragmentBinding
    private lateinit var adapter: UserDataAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.all_info_fragment, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        adapter = UserDataAdapter(
            UserDataClickListener { userDataAdapterEntity ->
                handleAdapterItemClick(userDataAdapterEntity)
            }
        )

        binding.allInfoRv.adapter = adapter

        lifecycleScope.launchWhenStarted {
            viewModel.listData.collect { data ->
                if (data.isEmpty()) {
                    binding.emptyViewGroup.visibility = View.VISIBLE
                    binding.emptyViewBackground.setImageResource(R.drawable.no_result)
                } else {
                    binding.emptyViewGroup.visibility = View.GONE
                    binding.emptyViewBackground.setImageResource(R.drawable.empty)
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
