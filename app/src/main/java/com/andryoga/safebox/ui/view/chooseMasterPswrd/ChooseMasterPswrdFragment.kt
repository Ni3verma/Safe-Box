package com.andryoga.safebox.ui.view.chooseMasterPswrd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.ChooseMasterPswrdFragmentBinding
import com.andryoga.safebox.ui.common.Utils
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.*
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ChooseMasterPswrdFragment : Fragment() {
    private val viewModel: ChooseMasterPswrdViewModel by viewModels()

    private lateinit var binding: ChooseMasterPswrdFragmentBinding
    private lateinit var pswrdValidatorMapping: Map<ChooseMasterPswrdValidationFailureCode, TextView>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.choose_master_pswrd_fragment,
            container,
            false
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        binding.pswrdText.addTextChangedListener {
            viewModel.evaluateValidationRules()
        }

        binding.confirmPswrdText.addTextChangedListener {
            viewModel.evaluateValidationRules()
        }

        binding.hintText.addTextChangedListener {
            viewModel.evaluateValidationRules()
        }

        initPasswordValidatorMapping()
        setupObservers()

        return binding.root
    }

    private fun setupObservers() {
        viewModel.validationFailureCode.observe(viewLifecycleOwner) { failureCode ->
            // by default make every validation as pass
            pswrdValidatorMapping.values.forEach { validationView ->
                Utils.setTextViewLeftDrawable(validationView, R.drawable.ic_check_24)
            }
            binding.hint.error = null

            // save button will be enabled only if there is no validation failure
            binding.saveBtn.isEnabled = failureCode.isNullOrEmpty()

            // change icon for those where validation failed
            Timber.d("failure validation codes : $failureCode")
            for (code in failureCode) {
                when {
                    pswrdValidatorMapping.containsKey(code) -> Utils.setTextViewLeftDrawable(
                        pswrdValidatorMapping[code]!!,
                        R.drawable.ic_error_24
                    )
                    code == HINT_IS_SUBSET -> {
                        binding.hint.isErrorEnabled = true
                        binding.hint.error = getString(R.string.hint_error)
                    }
                    else -> {
                        Timber.e("$code not found in validatorMapping")
                    }
                }
            }
        }

        viewModel.navigateToHome.observe(viewLifecycleOwner) { isNavigate ->
            if (isNavigate) {
                Timber.i("navigating to home")
                findNavController().navigate(R.id.action_chooseMasterPswrdFragment_to_homeFragment)
            }
        }
    }

    private fun initPasswordValidatorMapping() {
        pswrdValidatorMapping = mapOf(
            LOW_PASSWORD_LENGTH to binding.lengthValidation,
            LESS_SPECIAL_CHAR_COUNT to binding.specialCharValidation,
            NOT_MIX_CASE to binding.caseValidation,
            LESS_NUMERIC_COUNT to binding.numericValidation,
            ALTERNATE_CHAR_FOUND to binding.alternateValidation,
            PASSWORD_DO_NOT_MATCH to binding.pswrdMatchValidation
        )
    }
}
