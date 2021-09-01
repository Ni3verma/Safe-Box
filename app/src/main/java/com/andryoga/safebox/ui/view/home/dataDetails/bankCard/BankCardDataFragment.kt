package com.andryoga.safebox.ui.view.home.dataDetails.bankCard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.databinding.BankCardDataFragmentBinding
import com.andryoga.safebox.ui.common.CommonSnackbar
import com.andryoga.safebox.ui.common.RequiredFieldValidator
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.Status
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BankCardDataFragment : BottomSheetDialogFragment() {

    private val viewModel: BankCardDataViewModel by viewModels()
    private val args: BankCardDataFragmentArgs by navArgs()
    private lateinit var binding: BankCardDataFragmentBinding
    private val tagLocal = "add bank card dialog fragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.bank_card_data_fragment,
            container,
            false
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        Timber.i("received id = ${args.id}")
        viewModel.setRuntimeVar(args)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        /* whenever two characters(basically month) are entered in expiry date
        * then append "/" in the end.
        * Here start = 1 means I am entering 2nd character and
        * before = 0 means I am not deleting a character and moving backwards
        * */
        binding.expiryDateText.doOnTextChanged { _, start, before, _ ->
            if (start == 1 && before == 0) {
                binding.expiryDateText.append("/")
            }
        }

        binding.saveBtn.setOnClickListener {
            viewModel.onSaveClick().observe(viewLifecycleOwner) {
                handleSaveButtonClick(it)
            }
        }

        val requiredFieldValidator = RequiredFieldValidator(
            listOf(
                binding.title,
                binding.number,
                binding.cvv,
                binding.expiryDate
            ),
            binding.saveBtn,
            tagLocal
        )
        requiredFieldValidator.validate()
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
}
