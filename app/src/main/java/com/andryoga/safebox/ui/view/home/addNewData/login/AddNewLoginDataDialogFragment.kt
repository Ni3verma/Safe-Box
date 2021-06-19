package com.andryoga.safebox.ui.view.home.addNewData.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.databinding.DialogAddNewLoginDataBinding
import com.andryoga.safebox.ui.common.CommonSnackbar.showErrorSnackbar
import com.andryoga.safebox.ui.common.CommonSnackbar.showSuccessSnackbar
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.andryoga.safebox.ui.common.Utils.switchVisibility
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddNewLoginDataDialogFragment : BottomSheetDialogFragment() {
    private val viewModel: AddNewLoginDataViewModel by viewModels()
    private lateinit var binding: DialogAddNewLoginDataBinding
    private val tagLocal = "add new login data dialog fragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(
                inflater, R.layout.dialog_add_new_login_data,
                container, false
            )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        setupObservers()
        return binding.root
    }

    private fun setupObservers() {
        binding.saveBtn.setOnClickListener {
            viewModel.onSaveClick().observe(viewLifecycleOwner) {
                handleSaveButtonClick(it)
            }
        }
    }

    private fun handleSaveButtonClick(resource: Resource<Boolean>) {
        // FIXME: 6/19/2021 Not able to figure out why snackbar is not showing up
        Utils.logResourceInfo(tagLocal, resource)
        when (resource.status) {
            Status.LOADING -> switchVisibility(binding.saveBtn, binding.loading)
            Status.SUCCESS -> {
                showSuccessSnackbar(requireView(), "Data saved")
                dismiss()
            }
            Status.ERROR -> {
                switchVisibility(binding.saveBtn, binding.loading)
                showErrorSnackbar(requireView(), "Error occurred while saving data")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val requiredFieldValidator = RequiredFieldValidator(
            listOf(
                binding.title,
                binding.userId,
                binding.pswrd
            ),
            binding.saveBtn,
            tagLocal
        )
        requiredFieldValidator.validate()
    }
}
