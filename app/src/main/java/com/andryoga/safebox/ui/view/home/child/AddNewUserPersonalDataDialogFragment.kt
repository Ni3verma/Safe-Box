package com.andryoga.safebox.ui.view.home.child

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.AddNewUserPersonalDataDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber

class AddNewUserPersonalDataDialogFragment :
    BottomSheetDialogFragment() {
    private lateinit var binding: AddNewUserPersonalDataDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(
                inflater, R.layout.add_new_user_personal_data_dialog,
                container, false
            )
        binding.lifecycleOwner = this

        binding.newPersonalLoginData.setOnClickListener {
            Timber.i("opening login data details")
            findNavController().navigate(R.id.action_addNewUserPersonalDataDialogFragment_to_loginDataFragment)
        }
        binding.newPersonalBankAccountData.setOnClickListener {
            Timber.i("opening bank account data details")
            findNavController().navigate(R.id.action_addNewUserPersonalDataDialogFragment_to_bankAccountDataFragment)
        }
        binding.newPersonalBankCardData.setOnClickListener {
            Timber.i("opening bank card data details")
            findNavController().navigate(R.id.action_addNewUserPersonalDataDialogFragment_to_bankCardDataFragment)
        }
        binding.newPersonalNoteData.setOnClickListener {
            Timber.i("opening secure note data details")
            findNavController().navigate(R.id.action_addNewUserPersonalDataDialogFragment_to_secureNoteDataFragment)
        }

        return binding.root
    }
}
