package com.andryoga.safebox.ui.view.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Constants.time500Milli
import com.andryoga.safebox.databinding.HomeFragmentBinding
import com.andryoga.safebox.ui.common.Utils.startMotionLayoutTransition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var binding: HomeFragmentBinding
    private lateinit var motionLayout: MotionLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.home_fragment, container, false)
        motionLayout = binding.addNewUserPersonalDataLayout.motionLayout
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
            R.id.nav_all_info,
            R.id.nav_bank_account_info,
            R.id.nav_bank_card_info,
            R.id.nav_secure_note_info
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
        collapseAddNewDataOptions()
        when (item.itemId) {
            android.R.id.home -> {
                if (binding.drawerLayout.isOpen)
                    binding.drawerLayout.close()
                else
                    binding.drawerLayout.open()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupObservers() {
        binding.addNewUserPersonalDataLayout.newDataMasterFab.setOnClickListener {
            if (motionLayout.currentState == R.id.start) {
                startMotionLayoutTransition(motionLayout, R.id.end)
            } else {
                collapseAddNewDataOptions()
            }
        }

        binding.addNewUserPersonalDataLayout.background.setOnClickListener {
            collapseAddNewDataOptions()
        }
        viewModel.addNewUserDataOptionClicked.observe(viewLifecycleOwner) { viewId ->
            handleAddNewUserDataFabClick(viewId)
        }
    }

    private fun handleAddNewUserDataFabClick(viewId: Int) {
        when (viewId) {
            R.id.new_personal_login_data -> {
                Timber.i("opening add new login data")
                findNavController().navigate(R.id.action_homeFragment_to_addNewLoginDataDialogFragment)
            }
            R.id.new_personal_bank_account_data -> {
                Timber.i("opening add new bank account data")
                findNavController().navigate(R.id.action_homeFragment_to_addNewBankAccountDataDialogFragment)
            }
            R.id.new_personal_bank_card_data -> {
                Timber.i("opening add new bank card data")
                findNavController().navigate(R.id.action_homeFragment_to_addNewBankCardDialogFragment)
            }
            R.id.new_personal_note_data -> {
                Timber.i("opening add new secure note data")
                findNavController().navigate(R.id.action_homeFragment_to_secureNoteDataFragment)
            }
            else -> {
                Timber.w("no handler found for $viewId")
            }
        }

        lifecycleScope.launchWhenStarted {
            delay(time500Milli)
            collapseAddNewDataOptions()
        }
    }

    private fun collapseAddNewDataOptions() {
        startMotionLayoutTransition(motionLayout, R.id.start)
    }
}
