package com.andryoga.safebox.ui.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Constants.APP_GITHUB_URL
import com.andryoga.safebox.common.Constants.time500Milli
import com.andryoga.safebox.common.CrashlyticsKeys
import com.andryoga.safebox.databinding.ActivityMainBinding
import com.andryoga.safebox.ui.common.Utils
import com.andryoga.safebox.ui.view.MainActivity.Constants.LAST_INTERACTED_TIME
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    private var lastInteractionTime: Long = System.currentTimeMillis()

    private lateinit var binding: ActivityMainBinding
    private lateinit var motionLayout: MotionLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val navController by lazy {
        Navigation.findNavController(this, R.id.nav_host_fragment)
    }
    private val drawerLayoutTopLevelNavigationIds = setOf(
        R.id.loginFragment,
        R.id.chooseMasterPswrdFragment,
        R.id.nav_login_info,
        R.id.nav_all_info,
        R.id.nav_bank_account_info,
        R.id.nav_bank_card_info,
        R.id.nav_secure_note_info
    )
    private val drawerLayoutFirstScreen = R.id.nav_all_info

    object Constants {
        const val LAST_INTERACTED_TIME = "last_interacted_time"
        const val MAX_USER_AWAY_MILLI_SECONDS = 20000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("on create activity")
        // add secure flag so that view is not visible in recent app screen
        // NOTE : this will also restrict user to take screenshots inside app
        // could be USER_PREFERENCE in future
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        lastInteractionTime = savedInstanceState?.getLong(LAST_INTERACTED_TIME)
            ?: System.currentTimeMillis()
        checkUserAwayTimeout()

        motionLayout = binding.addNewUserPersonalDataLayout.motionLayout
        drawerLayout = binding.drawerLayout
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setupObservers()

        // top level navigation for which back button should not appear
        appBarConfiguration = AppBarConfiguration(
            drawerLayoutTopLevelNavigationIds,
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
        binding.navView.setNavigationItemSelectedListener {
            val id = it.itemId
            if (id == drawerLayoutFirstScreen) {
                navController.popBackStack(drawerLayoutFirstScreen, true)
            } else if (id in drawerLayoutTopLevelNavigationIds) {
                navController.popBackStack(drawerLayoutFirstScreen, false)
            } else if (id == R.id.open_git) {
                openGithubPage()
                drawerLayout.closeDrawers()
                // return false so that this option is not selected
                return@setNavigationItemSelectedListener false
            }
            navController.navigate(id)
            drawerLayout.closeDrawers()
            true
        }

        CrashlyticsKeys(this).setDefaultKeys()
    }

    override fun onPause() {
        super.onPause()
        lastInteractionTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        checkUserAwayTimeout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(LAST_INTERACTED_TIME, System.currentTimeMillis())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        collapseAddNewDataOptions()
        when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isOpen)
                    drawerLayout.close()
                else
                    drawerLayout.open()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupObservers() {
        binding.addNewUserPersonalDataLayout.newDataMasterFab.setOnClickListener {
            if (motionLayout.currentState == R.id.start) {
                Utils.startMotionLayoutTransition(motionLayout, R.id.end)
            } else {
                collapseAddNewDataOptions()
            }
        }

        binding.addNewUserPersonalDataLayout.background.setOnClickListener {
            collapseAddNewDataOptions()
        }
        viewModel.addNewUserDataOptionClicked.observe(this) { viewId ->
            handleAddNewUserDataFabClick(viewId)
        }
    }

    private fun handleAddNewUserDataFabClick(viewId: Int) {
        when (viewId) {
            R.id.new_personal_login_data -> {
                Timber.i("opening login data details")
                navController.navigate(R.id.action_global_loginDataFragment)
            }
            R.id.new_personal_bank_account_data -> {
                Timber.i("opening bank account data details")
                navController.navigate(R.id.action_global_bankAccountDataFragment)
            }
            R.id.new_personal_bank_card_data -> {
                Timber.i("opening bank card data details")
                navController.navigate(R.id.action_global_bankCardDataFragment)
            }
            R.id.new_personal_note_data -> {
                Timber.i("opening secure note data details")
                navController.navigate(R.id.action_global_secureNoteDataFragment)
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
        Utils.startMotionLayoutTransition(motionLayout, R.id.start)
    }

    fun setAddNewUserDataVisibility(isVisible: Boolean) {
        Timber.i("setting add new user data visibility to $isVisible")
        binding.addNewUserPersonalDataLayout.motionLayout.visibility =
            if (isVisible) View.VISIBLE else View.INVISIBLE
    }

    fun setSupportActionBarVisibility(isVisible: Boolean) {
        Timber.i("setting support action bar visibility to $isVisible")
        if (isVisible) supportActionBar?.show() else supportActionBar?.hide()
    }

    // this is directly called from xml so view input param is required
    // name=expected is used so that detekt dont complain about it
    fun openGithub(expected: View) {
        openGithubPage()
    }

    private fun openGithubPage() {
        Timber.i("opening app github page")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APP_GITHUB_URL))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    /*Check if user is away for too long
    * @returns true if timeout has happened
    * */
    fun checkUserAwayTimeout(): Boolean {
        return if ((System.currentTimeMillis() - lastInteractionTime) > Constants.MAX_USER_AWAY_MILLI_SECONDS) {
            Timber.i("away timeout, showing dialog")
            binding.hideView.visibility = View.VISIBLE
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.timeout_dialog_title))
                .setMessage(getString(R.string.timeout_dialog_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.timeout_dialog_positive_button_text)) { _, _ ->
                    Timber.i("ok clicked, restarting app")
                    finish()
                    startActivity(intent)
                }.show()
            true
        } else false
    }
}
