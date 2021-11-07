package com.andryoga.safebox.ui.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Constants.APP_GITHUB_URL
import com.andryoga.safebox.common.CrashlyticsKeys
import com.andryoga.safebox.databinding.ActivityMainBinding
import com.andryoga.safebox.ui.common.Utils.hideSoftKeyboard
import com.andryoga.safebox.ui.view.MainActivity.Constants.AUTO_UPDATE_REQUEST_CODE
import com.andryoga.safebox.ui.view.MainActivity.Constants.DAYS_FOR_UPDATE
import com.andryoga.safebox.ui.view.MainActivity.Constants.LAST_INTERACTED_TIME
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var reviewInfo: ReviewInfo? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var reviewManager: ReviewManager
    private lateinit var appUpdateManager: AppUpdateManager

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

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            snackbarForCompleteUpdate()
        }
    }

    object Constants {
        const val LAST_INTERACTED_TIME = "last_interacted_time"
        const val MAX_USER_AWAY_MILLI_SECONDS = 20000L
        const val AUTO_UPDATE_REQUEST_CODE = 11
        const val DAYS_FOR_UPDATE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("on create activity")
        // add secure flag so that view is not visible in recent app screen
        // NOTE : this will also restrict user to take screenshots inside app
        // could be USER_PREFERENCE in future
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        appUpdateManager = AppUpdateManagerFactory.create(this)

        lastInteractionTime = savedInstanceState?.getLong(LAST_INTERACTED_TIME)
            ?: System.currentTimeMillis()
        checkUserAwayTimeout()

        drawerLayout = binding.drawerLayout
        binding.lifecycleOwner = this

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
        prefetchReviewInfo()

        appUpdate()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTO_UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK)
                Toast.makeText(this, getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
        }
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
        when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isOpen) {
                    drawerLayout.close()
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        hideSoftKeyboard(this)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
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

    private fun prefetchReviewInfo() {
        reviewManager = ReviewManagerFactory.create(this)
        val reviewInfoTask = reviewManager.requestReviewFlow()
        reviewInfoTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Timber.i("review info fetched")
                reviewInfo = task.result
            } else {
                Timber.w("error while requesting app review flow", task.exception)
            }
        }
    }

    fun launchReviewFlow(nextFunction: () -> Unit) {
        if (this::reviewManager.isInitialized && reviewInfo != null) {
            val flow = reviewManager.launchReviewFlow(this, reviewInfo!!)
            flow.addOnCompleteListener { _ ->
                Timber.i("review flow done")
                nextFunction()
            }
        } else {
            nextFunction()
        }
    }

    private fun appUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                (appUpdateInfo.clientVersionStalenessDays() ?: -1) >= DAYS_FOR_UPDATE
            ) {
                Timber.i("Update Available")
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE, this, AUTO_UPDATE_REQUEST_CODE
                )
                appUpdateManager.registerListener(listener)
            }
        }
    }

    private fun snackbarForCompleteUpdate() {
        Snackbar.make(findViewById(R.id.content), "App update has been downloaded", Snackbar.LENGTH_INDEFINITE)
            .setAction(getString(R.string.install)) {
                appUpdateManager.completeUpdate()
            }.show()
    }
}
