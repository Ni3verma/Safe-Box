package com.andryoga.safebox.ui.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.utils.findActivity

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

fun canAuthenticateUsingBiometric(context: Context): Boolean {
    return BiometricManager.from(context)
        .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
fun BiometricAuthHandler(
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity } ?: return
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    val biometricPrompt: BiometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // not handling this for now
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
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
