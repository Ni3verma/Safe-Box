package com.andryoga.composeapp.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.ui.core.AnimatedCurveBackground
import com.andryoga.composeapp.ui.navigation.AppNavigation
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeBoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    val viewModel = hiltViewModel<MainActivityViewModel>()
                    val startDestinationState by viewModel.startDestination.collectAsState()
                    AppNavigation(
                        startDestinationState = startDestinationState,
//                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedCurveBackground()
        CircularProgressIndicator()
    }
}

@Preview
@Composable
private fun LoadingScreenPreview() {
    SafeBoxTheme {
        LoadingScreen()
    }
}

@Serializable
object LoadingRoute