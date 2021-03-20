package com.andryoga.safebox.ui.view.home.child.all

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.AllInfoFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AllInfoFragment : Fragment() {
    private val viewModel: AllInfoViewModel by viewModels()

    private lateinit var binding: AllInfoFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.all_info_fragment, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        return binding.root
    }
}