package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.databinding.DialogAddNewBankCardDataBinding
import com.andryoga.safebox.ui.common.*
import com.andryoga.safebox.ui.theme.BasicSafeBoxTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AddNewBankCardDialogFragment : BottomSheetDialogFragment() {

    private val viewModel: AddNewBankCardViewModel by viewModels()
    private lateinit var binding: DialogAddNewBankCardDataBinding
    private val tagLocal = "add new bank card dialog fragment"

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
        binding.composeView.setContent {
            BasicSafeBoxTheme {
                initSelectBankAccountDialog()
            }
        }

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

    @Composable
    private fun initSelectBankAccountDialog() {
        val bankAccountData by viewModel.bankAccounts.collectAsState(emptyList())
        CommonDialog(
            isShown = viewModel.showSelectBankAccountDialog,
            title = "Select Bank Account",
            onDialogDismiss = { viewModel.switchSelectBankAccountDialog() },
            onPrimaryButtonClick = { viewModel.switchSelectBankAccountDialog() }
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(Dp.Unspecified, 500.dp)
                        .padding(8.dp)
                ) {
                    items(
                        items = bankAccountData,
                        key = { bankAccount -> bankAccount.key }
                    ) { item ->
                        Column(
                            Modifier.clickable {
                                Timber.i("clicked ${item.title} bank account")
                                viewModel.addNewBankCardScreenData.linkedBankAccount = item.key
                                binding.linkedBankAccount.text =
                                    String(StringBuilder("${item.title}\n${item.accountNumber}"))
                                viewModel.switchSelectBankAccountDialog()
                            }
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.h6
                            )
                            Text(
                                item.accountNumber,
                                style = MaterialTheme.typography.body1,
                            )
                            Divider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                        }
                    }
                }
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
}
