package com.andryoga.safebox.ui.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.ui.MainActivityViewModel
import com.andryoga.safebox.ui.core.AnimatedCurveBackground
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import timber.log.Timber

@Composable
fun LoadingScreenRoot(
    navigateToLogin: () -> Unit,
    navigateToSignup: () -> Unit
) {
    val viewModel = hiltViewModel<MainActivityViewModel>()
    val loadingState by viewModel.loadingState.collectAsState(LoadingState.Initial)

    Timber.i("loading state: $loadingState")
    when (loadingState) {
        LoadingState.Initial -> LoadingScreen()
        LoadingState.ProceedToLogin -> navigateToLogin()
        LoadingState.ProceedToSignup -> navigateToSignup()
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedCurveBackground()
        CircularProgressIndicator()
    }
}

@LightDarkModePreview
@Composable
private fun LoadingScreenPreview() {
    SafeBoxTheme {
        LoadingScreen()
    }
}