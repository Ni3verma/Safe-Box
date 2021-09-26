package com.andryoga.safebox.ui.view.chooseMasterPswrd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.ChooseMasterPswrdFragmentBinding
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import timber.log.Timber

@AndroidEntryPoint
class ChooseMasterPswrdFragment : Fragment() {
    private val viewModel: ChooseMasterPswrdViewModel by viewModels()

    private lateinit var binding: ChooseMasterPswrdFragmentBinding
    private lateinit var validatorErrorMessageMap: Map<ChooseMasterPswrdValidationFailureCode, String>

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

        binding.hintText.addTextChangedListener {
            viewModel.evaluateValidationRules()
        }

        initPasswordValidatorMapping()
        setupObservers()

        return binding.root
    }

    private fun setupObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.validationFailureCode.collect { failureCode ->
                if (failureCode == null && viewModel.pswrd.value.isBlank()) {
                    // do nothing
                } else if (failureCode == null) {
                    binding.apply {
                        saveBtn.isEnabled = true
                        pswrd.apply {
                            isErrorEnabled = false
                            isHelperTextEnabled = true
                            helperText = context.getString(R.string.password_is_ok)
                        }
                        hint.apply {
                            isErrorEnabled = false
                            isHelperTextEnabled = true
                            helperText = context.getString(R.string.hint_is_ok)
                        }
                    }
                } else if (failureCode == HINT_IS_SUBSET) {
                    binding.saveBtn.isEnabled = false
                    binding.hint.apply {
                        isErrorEnabled = true
                        error = validatorErrorMessageMap.getOrDefault(
                            failureCode,
                            context.getString(
                                R.string.error
                            )
                        )
                        isHelperTextEnabled = false
                    }
                } else {
                    binding.saveBtn.isEnabled = false
                    binding.pswrd.apply {
                        isErrorEnabled = true
                        error = validatorErrorMessageMap.getOrDefault(
                            failureCode,
                            context.getString(
                                R.string.error
                            )
                        )
                        isHelperTextEnabled = false
                    }
                }
            }
        }

        viewModel.navigateToHome.observe(viewLifecycleOwner) { isNavigate ->
            if (isNavigate) {
                Timber.i("navigating to home")
                findNavController().navigate(R.id.action_chooseMasterPswrdFragment_to_nav_all_info)
            }
        }
    }

    private fun initPasswordValidatorMapping() {
        validatorErrorMessageMap = mapOf(
            LOW_PASSWORD_LENGTH to getString(R.string.length_validation_text),
            LESS_SPECIAL_CHAR_COUNT to getString(R.string.special_char_validation_text),
            NOT_MIX_CASE to getString(R.string.case_validation_text),
            LESS_NUMERIC_COUNT to getString(R.string.numeric_validation_text),
            ALTERNATE_CHAR_FOUND to getString(R.string.alternate_validation_text),
            HINT_IS_SUBSET to getString(R.string.hint_subset_validation_text)
        )
    }
}
