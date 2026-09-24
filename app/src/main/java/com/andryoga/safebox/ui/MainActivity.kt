package com.andryoga.safebox.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.ui.core.DeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.TestTags
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
    lateinit var biometricAuthProvider: DeviceSecurityAuthProvider

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
        val exposeTestTags = TestTags.shouldExposeAsResourceId(BuildConfig.BUILD_TYPE)
        setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides biometricAuthProvider) {
                SafeBoxTheme {
                    // Makes Modifier.testTag visible to UI Automator as a resource id, so black-box
                    // tests can find controls that carry no text. Dialogs are separate windows and
                    // do not inherit this; see TestTags for which builds turn it on and why.
                    Box(Modifier.semantics { testTagsAsResourceId = exposeTestTags }) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
@Serializable
object LoadingRoute