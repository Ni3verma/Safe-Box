package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.DialogAddNewBankCardDataBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber

class AddNewBankCardDialogFragment : BottomSheetDialogFragment() {

    private val viewModel: AddNewBankCardViewModel by viewModels()
    private lateinit var binding: DialogAddNewBankCardDataBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.dialog_add_new_bank_card_data,
            container,
            false
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.expiryDateText.doAfterTextChanged { currentText ->
            if (currentText?.toString()?.length == 2) {
                // gotta handle this
                Timber.i("$currentText")
            }
        }
    }
}
