package com.andryoga.safebox.ui.view.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.LoginFragmentBinding
import com.andryoga.safebox.ui.common.Biometricable
import com.andryoga.safebox.ui.common.BiometricableEventType
import com.andryoga.safebox.ui.common.Utils.hideSoftKeyboard
import com.andryoga.safebox.ui.common.biometricableHandler
import com.andryoga.safebox.ui.view.MainActivity
import com.andryoga.safebox.ui.view.login.LoginViewModel.Constants.ASK_FOR_REVIEW_AFTER_EVERY
import com.andryoga.safebox.ui.view.login.LoginViewModel.Constants.MAX_CONT_BIOMETRIC_LOGINS
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class LoginFragment : Fragment(), Biometricable by biometricableHandler() {
    private val viewModel: LoginViewModel by viewModels()

    private lateinit var binding: LoginFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.login_fragment, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        configureBiometrics(this, this)

        binding.pswrdText.addTextChangedListener {
            if (binding.pswrd.isErrorEnabled) {
                Timber.i("clearing error on pswrd edit text")
                binding.pswrd.isErrorEnabled = false
                binding.pswrd.error = null
            }
        }

        if (viewModel.isSignUpRequired) {
            Timber.i("signup required")
            findNavController().navigate(R.id.action_loginFragment_to_chooseMasterPswrdFragment)
        } else {
            Timber.i("no signup required")
        }

        setupObservers()

        binding.ShowHintText.setOnClickListener {
            viewModel.getHintFromDb()
            binding.ShowHintText.isEnabled = false
            binding.ShowHintText.text = getString(R.string.hint)
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        Timber.i("on start of login fragment")
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).apply {
                setSupportActionBarVisibility(false)
            }
        } else {
            Timber.w("activity expected was MainActivity but was ${requireActivity().localClassName}")
        }
    }

    override fun onResume() {
        super.onResume()
        /* show biometric dialog only if device can authenticate with biometric
         * and login count with biometric has not crossed threshold
         * and user away timeout has not happened
         * */
        val canUnlockWithBiometric = canUseBiometrics()
        Timber.i("can unlock with biometric = $canUnlockWithBiometric")
        if (canUnlockWithBiometric &&
            viewModel.loginCountWithBiometric < MAX_CONT_BIOMETRIC_LOGINS &&
            !(requireActivity() as MainActivity).checkUserAwayTimeout()
        ) {
            Timber.i("showing biometric dialog")
            showBiometricsAuthDialog(
                getString(R.string.biometric_title_text),
                getString(R.string.biometric_negative_button_text)
            )
        } else if (canUnlockWithBiometric && viewModel.loginCountWithBiometric >= MAX_CONT_BIOMETRIC_LOGINS) {
            Timber.i(
                "not showing biometric dialog, cont. count" +
                    " with biometric = ${viewModel.loginCountWithBiometric}\n" +
                    "showing enter password manually message"
            )
            binding.enterPasswordManuallyMessage.visibility = View.VISIBLE
        }
    }

    override fun onBiometricEvent(event: BiometricableEventType) {
        when (event) {
            BiometricableEventType.AUTHENTICATION_SUCCEEDED -> {
                Timber.i("User authenticated with biometric")
                viewModel.onUnlockedWithBiometric()
            }
            else -> {
                Timber.i("biometric event failed, name = ${event.name}")
            }
        }
    }

    private fun setupObservers() {
        viewModel.isWrongPswrdEntered.observe(viewLifecycleOwner) {
            if (it) {
                binding.pswrd.error = "Wrong password!"
                binding.pswrd.isErrorEnabled = true
            }
        }

        viewModel.navigateToHome.observe(viewLifecycleOwner) { isNavigate ->
            if (isNavigate) {
                if (viewModel.totalLoginCount < 0 || viewModel.totalLoginCount % ASK_FOR_REVIEW_AFTER_EVERY == 0) {
                    (requireActivity() as MainActivity).launchReviewFlow { navigateToHome() }
                } else {
                    navigateToHome()
                }
            }
        }
    }

    private fun navigateToHome() {
        Timber.i("hiding keyboard")
        hideSoftKeyboard(requireActivity())
        Timber.i("navigating to home")
        findNavController().navigate(R.id.action_loginFragment_to_nav_all_info)
    }
}
