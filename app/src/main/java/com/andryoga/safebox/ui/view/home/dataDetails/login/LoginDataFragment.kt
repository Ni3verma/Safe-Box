package com.andryoga.safebox.ui.view.home.dataDetails.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.databinding.LoginDataFragmentBinding
import com.andryoga.safebox.ui.common.CommonSnackbar.showErrorSnackbar
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.Utils.hideSoftKeyboard
import com.andryoga.safebox.ui.common.Utils.switchVisibility
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class LoginDataFragment : Fragment() {
    private val viewModel: LoginDataViewModel by viewModels()
    private val args: LoginDataFragmentArgs by navArgs()
    private lateinit var binding: LoginDataFragmentBinding

    private val tagLocal = "login data fragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding =
            DataBindingUtil.inflate(
                inflater,
                R.layout.login_data_fragment,
                container,
                false,
            )
        binding.screenData = viewModel.loginScreenData
        binding.lifecycleOwner = this
        Timber.i("$tagLocal : received id = ${args.id}")
        viewModel.setRuntimeVar(args)
        setupObservers()
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val requiredFieldValidator =
            RequiredFieldValidator(
                listOf(
                    binding.title,
                    binding.userId,
                ),
                binding.saveBtn,
                tagLocal,
            )
        requiredFieldValidator.validate()
    }

    private fun setupObservers() {
        binding.saveBtn.setOnClickListener {
            viewModel.onSaveClick().observe(viewLifecycleOwner) {
                handleSaveButtonClick(it)
            }
        }
    }

    private fun handleSaveButtonClick(resource: Resource<Boolean>) {
        Timber.i("save clicked")
        // NEED_HELP: 6/19/2021 Not able to figure out
        // why snackbar is not showing up if I use requireView() in view param
        Utils.logResource(tagLocal, resource)
        when (resource.status) {
            Status.LOADING -> switchVisibility(binding.saveBtn, binding.loading)
            Status.SUCCESS -> {
                hideSoftKeyboard(requireActivity())
                findNavController().navigateUp()
            }
            Status.ERROR -> {
                switchVisibility(binding.saveBtn, binding.loading)
                showErrorSnackbar(
                    requireActivity().findViewById(R.id.drawer_layout),
                    getString(R.string.snackbar_common_error_saving_data),
                )
                hideSoftKeyboard(requireActivity())
                findNavController().navigateUp()
            }
        }
    }
}
