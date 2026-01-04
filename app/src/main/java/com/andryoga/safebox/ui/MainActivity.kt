package com.andryoga.safebox.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.andryoga.safebox.ui.navigation.AppNavigation
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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