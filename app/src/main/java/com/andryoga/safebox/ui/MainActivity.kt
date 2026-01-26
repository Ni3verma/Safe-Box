package com.andryoga.safebox.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andryoga.safebox.ui.navigation.AppNavigation
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber

@AndroidEntryPoint
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isPrivacyEnabled.collect { isEnabled ->
                    Timber.i("setting flag secure to $isEnabled")
                    if (isEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            SafeBoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    AppNavigation()
                }
            }
        }
    }
}
@Serializable
object LoadingRoute