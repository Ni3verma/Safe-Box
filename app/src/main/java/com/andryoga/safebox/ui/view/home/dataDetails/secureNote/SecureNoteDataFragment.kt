package com.andryoga.safebox.ui.view.home.dataDetails.secureNote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.databinding.DialogSecureNoteDataBinding
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SecureNoteDataFragment : BottomSheetDialogFragment() {
    private val viewModel: SecureNoteDataViewModel by viewModels()
    private lateinit var binding: DialogSecureNoteDataBinding
    private val tagLocal = "add new secure note fragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.dialog_secure_note_data,
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
        // NEED_HELP: 6/19/2021 Not able to figure out
        // why snackbar is not showing up if I use requireView() in view param
        Utils.logResourceInfo(tagLocal, resource)
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
                dismiss()
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
                dismiss()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val requiredFieldValidator = RequiredFieldValidator(
            listOf(
                binding.title,
                binding.notes
            ),
            binding.saveBtn,
            tagLocal
        )
        requiredFieldValidator.validate()
    }
}
