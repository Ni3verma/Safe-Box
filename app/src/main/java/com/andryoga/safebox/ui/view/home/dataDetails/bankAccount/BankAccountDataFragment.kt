package com.andryoga.safebox.ui.view.home.dataDetails.bankAccount

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
import com.andryoga.safebox.databinding.BankAccountDataFragmentBinding
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.Utils.hideSoftKeyboard
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BankAccountDataFragment : Fragment() {
    private val viewModel: BankAccountDataViewModel by viewModels()
    private val args: BankAccountDataFragmentArgs by navArgs()
    private lateinit var binding: BankAccountDataFragmentBinding
    private val tagLocal = "bank account data fragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(
                inflater, R.layout.bank_account_data_fragment,
                container, false
            )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        Timber.i("$tagLocal : received id = ${args.id}")
        viewModel.setRuntimeVar(args)
        setupObservers()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val requiredFieldValidator = RequiredFieldValidator(
            listOf(
                binding.title,
                binding.accountNo
            ),
            binding.saveBtn,
            tagLocal
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
            Status.LOADING -> com.andryoga.safebox.ui.common.Utils.switchVisibility(
                binding.saveBtn,
                binding.loading
            )
            Status.SUCCESS -> {
                CommonSnackbar.showSuccessSnackbar(
                    activity!!.findViewById(R.id.drawer_layout),
                    getString(R.string.snackbar_common_data_saved)
                )
                hideSoftKeyboard(requireActivity())
                findNavController().navigateUp()
            }
            Status.ERROR -> {
                com.andryoga.safebox.ui.common.Utils.switchVisibility(
                    binding.saveBtn,
                    binding.loading
                )
                CommonSnackbar.showErrorSnackbar(
                    activity!!.findViewById(R.id.drawer_layout),
                    getString(R.string.snackbar_common_error_saving_data)
                )
                hideSoftKeyboard(requireActivity())
                findNavController().navigateUp()
            }
        }
    }
}
