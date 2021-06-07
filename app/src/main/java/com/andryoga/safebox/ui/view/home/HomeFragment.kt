package com.andryoga.safebox.ui.view.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.databinding.HomeFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var binding: HomeFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.home_fragment, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        setHasOptionsMenu(true)

        setupObservers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // top level navigation for which back button should not appear
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_login_info,
            R.id.nav_all_info
        ).setOpenableLayout(binding.drawerLayout).build()

        val navController = requireActivity().findNavController(R.id.home_nav_host_fragment)
        binding.navView.setupWithNavController(navController)
        NavigationUI.setupActionBarWithNavController(
            (requireActivity() as AppCompatActivity),
            navController,
            appBarConfiguration
        )

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (binding.drawerLayout.isOpen) {
                    binding.drawerLayout.close()
                } else
                    binding.drawerLayout.open()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupObservers() {
        viewModel.masterOptionClicked.observe(viewLifecycleOwner) { isExpanded ->
            if (isExpanded) {
                binding.addNewUserPersonalDataLayout.newDataMasterFab.apply {
                    setIconResource(R.drawable.ic_cancel_24)
                    extend()
                }
            } else {
                binding.addNewUserPersonalDataLayout.newDataMasterFab.apply {
                    setIconResource(R.drawable.ic_add_24)
                    shrink()
                }
            }
        }
    }
}
