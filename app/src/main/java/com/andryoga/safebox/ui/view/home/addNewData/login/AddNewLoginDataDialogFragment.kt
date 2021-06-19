package com.andryoga.safebox.ui.view.home.addNewData.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.DialogAddNewLoginDataBinding
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddNewLoginDataDialogFragment : BottomSheetDialogFragment() {
    private val viewModel: AddNewLoginDataViewModel by viewModels()
    private lateinit var binding: DialogAddNewLoginDataBinding

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val requiredFieldValidator = RequiredFieldValidator(
            listOf(
                binding.title,
                binding.userId,
                binding.pswrd
            ),
            binding.saveBtn,
            "add new login data dialog fragment"
        )
        requiredFieldValidator.validate()
    }
}
