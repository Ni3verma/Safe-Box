package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.andryoga.safebox.R

class AddNewBankCardDialogFragment : Fragment() {

    companion object {
        fun newInstance() = AddNewBankCardDialogFragment()
    }

    private lateinit var viewModel: AddNewBankCardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_new_bank_card_data, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(AddNewBankCardViewModel::class.java)
    }
}
