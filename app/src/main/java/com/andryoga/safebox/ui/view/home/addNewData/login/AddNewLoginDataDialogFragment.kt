package com.andryoga.safebox.ui.view.home.addNewData.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
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
import timber.log.Timber

@AndroidEntryPoint
class AddNewLoginDataDialogFragment : BottomSheetDialogFragment() {
    private val viewModel: AddNewLoginDataViewModel by viewModels()
    private val args: AddNewLoginDataDialogFragmentArgs by navArgs()
    private lateinit var binding: DialogAddNewLoginDataBinding
    private val tagLocal = "Nitin add new login data dialog fragment"

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
        binding.screenData = viewModel.loginScreenData
        binding.lifecycleOwner = this
        Timber.i("received id = ${args.id}")
        viewModel.setRuntimeVar(args)
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
        // NEED_HELP: 6/19/2021 Not able to figure out
        // why snackbar is not showing up if I use requireView() in view param
        Utils.logResourceInfo(tagLocal, resource)
        when (resource.status) {
            Status.LOADING -> switchVisibility(binding.saveBtn, binding.loading)
            Status.SUCCESS -> {
                showSuccessSnackbar(
                    activity!!.findViewById(R.id.drawer_layout),
                    getString(R.string.snackbar_common_data_saved)
                )
                dismiss()
            }
            Status.ERROR -> {
                switchVisibility(binding.saveBtn, binding.loading)
                showErrorSnackbar(
                    activity!!.findViewById(R.id.drawer_layout),
                    getString(R.string.snackbar_common_error_saving_data)
                )
                dismiss()
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
