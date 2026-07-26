package com.andryoga.safebox.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.andryoga.safebox.ui.core.BiometricAuthProvider
import com.andryoga.safebox.ui.core.LocalBiometricAuthProvider
import com.andryoga.safebox.ui.navigation.AppNavigation
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var biometricAuthProvider: BiometricAuthProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.isPrivacyEnabled.collect { isEnabled ->
                Timber.i("setting flag secure to $isEnabled")
                if (isEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalBiometricAuthProvider provides biometricAuthProvider) {
                SafeBoxTheme {
                    AppNavigation()
                }
            }
        }
    }
}
@Serializable
object LoadingRoute