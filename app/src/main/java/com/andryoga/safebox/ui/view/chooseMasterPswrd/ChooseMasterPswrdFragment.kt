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
import com.andryoga.safebox.ui.view.chooseMasterPswrd.PasswordValidationFailureCode.*
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.*

@AndroidEntryPoint
class ChooseMasterPswrdFragment : Fragment() {

    object Constants {
        const val maxSimilarLength = 5
    }

    private val viewModel: ChooseMasterPswrdViewModel by viewModels()

    private lateinit var binding: ChooseMasterPswrdFragmentBinding
    private lateinit var validatorMapping: Map<PasswordValidationFailureCode, TextView>

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

        binding.hintText.addTextChangedListener {
            val similarLength = longestCommonSubstring(
                binding.hintText.text.toString().toLowerCase(),
                binding.pswrdText.text.toString().toLowerCase()
            )

            if (similarLength >= Constants.maxSimilarLength) {
                binding.hint.isErrorEnabled = true
                binding.hint.error = getString(R.string.hint_error)
                binding.saveBtn.isEnabled = false
            } else {
                binding.hint.isErrorEnabled = false
                binding.hint.error = null
                binding.saveBtn.isEnabled = true
            }
        }

        initValidatorMapping()
        setupObservers()

        return binding.root
    }

    private fun longestCommonSubstring(hintText: String, pswrdText: String): Int {
        val matrix = Array(hintText.length + 1) {
            IntArray(pswrdText.length + 1)
        }
        var maxLength = 0
        for (i in 1 until matrix.size) {
            for (j in 1 until matrix[0].size) {
                val text1 = hintText[i - 1]
                val text2 = pswrdText[j - 1]
                if (text1 != text2) {
                    matrix[i][j] = 0
                } else {
                    matrix[i][j] = matrix[i - 1][j - 1] + 1
                }
                if (matrix[i][j] > maxLength) {
                    maxLength = matrix[i][j]
                }
            }
        }
        return maxLength
    }

    private fun setupObservers() {
        viewModel.pswrdValidationFailures.observe(viewLifecycleOwner) { failureCode ->
            // by default make every validation as pass
            validatorMapping.values.forEach { validationView ->
                Utils.setTextViewLeftDrawable(validationView, R.drawable.ic_check_24)
            }

            // change icon for those where validation failed
            Timber.d("failure validation codes : $failureCode")
            for (code in failureCode) {
                if (validatorMapping.containsKey(code))
                    Utils.setTextViewLeftDrawable(validatorMapping[code]!!, R.drawable.ic_error_24)
                else {
                    Timber.e("$code not found in validatorMapping")
                }
            }
        }

        viewModel.isBothPasswordsMatch.observe(viewLifecycleOwner) { isMatch ->
            if (isMatch) {
                Utils.setTextViewLeftDrawable(binding.pswrdMatchValidation, R.drawable.ic_check_24)
            } else {
                Utils.setTextViewLeftDrawable(binding.pswrdMatchValidation, R.drawable.ic_error_24)
            }
        }

        viewModel.navigateToHome.observe(viewLifecycleOwner) { isNavigate ->
            if (isNavigate) {
                Timber.i("navigating to home")
                findNavController().navigate(R.id.action_chooseMasterPswrdFragment_to_homeFragment)
            }
        }
    }

    private fun initValidatorMapping() {
        validatorMapping = mapOf(
            LOW_PASSWORD_LENGTH to binding.lengthValidation,
            LESS_SPECIAL_CHAR_COUNT to binding.specialCharValidation,
            NOT_MIX_CASE to binding.caseValidation,
            LESS_NUMERIC_COUNT to binding.numericValidation,
            ALTERNATE_CHAR_FOUND to binding.alternateValidation,
            PASSWORD_DO_NOT_MATCH to binding.pswrdMatchValidation
        )
    }
}
