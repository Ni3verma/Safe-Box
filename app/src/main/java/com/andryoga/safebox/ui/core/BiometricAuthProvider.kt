package com.andryoga.safebox.ui.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.utils.findActivity
import javax.inject.Inject

val LocalBiometricAuthProvider = staticCompositionLocalOf<BiometricAuthProvider> {
    DefaultBiometricAuthProvider()
}

interface BiometricAuthProvider {
    fun canAuthenticate(context: Context): Boolean

    @Composable
    fun Authenticate(
        onSuccess: () -> Unit,
        onErrorOrCancel: () -> Unit
    )
}

class DefaultBiometricAuthProvider @Inject constructor() : BiometricAuthProvider {
    override fun canAuthenticate(context: Context): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    @Composable
    override fun Authenticate(
        onSuccess: () -> Unit,
        onErrorOrCancel: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() as? FragmentActivity } ?: return
        val executor = remember(context) { ContextCompat.getMainExecutor(context) }

        val currentOnSuccess by rememberUpdatedState(onSuccess)
        val currentOnErrorOrCancel by rememberUpdatedState(onErrorOrCancel)

        val biometricPrompt: BiometricPrompt = remember(activity) {
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        currentOnSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        currentOnErrorOrCancel()
                    }
                }
            )
        }

        val title = stringResource(R.string.biometric_title_text)
        val subtitle = stringResource(R.string.biometric_sub_title_text)
        val negativeButtonText = stringResource(R.string.biometric_negative_button_text)
        LaunchedEffect(biometricPrompt) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }
}
