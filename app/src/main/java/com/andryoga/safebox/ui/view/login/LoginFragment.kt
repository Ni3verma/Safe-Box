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
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private val viewModel: LoginViewModel by viewModels()

    private lateinit var binding: LoginFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.login_fragment, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        binding.pswrdText.addTextChangedListener {
            Timber.i("clearing error(if any) on pswrd edit text")
            binding.pswrd.isErrorEnabled = false
            binding.pswrd.error = null
        }

        if (viewModel.isSignUpRequired) {
            Timber.i("signup required")
            findNavController().navigate(R.id.action_loginFragment_to_chooseMasterPswrdFragment)
        } else {
            Timber.i("no signup required")
        }

        setupObservers()

        return binding.root
    }

    private fun setupObservers() {
        viewModel.isWrongPswrdEntered.observe(viewLifecycleOwner) {
            if (it) {
                binding.pswrd.error = "Wrong password!"
                binding.pswrd.isErrorEnabled = true
            }
        }
    }
}