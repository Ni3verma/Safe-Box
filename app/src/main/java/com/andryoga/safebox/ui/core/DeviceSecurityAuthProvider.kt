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

private fun getAllowedAuthenticators(allowDeviceCredential: Boolean): Int {
    return if (allowDeviceCredential) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}

val LocalDeviceSecurityAuthProvider = staticCompositionLocalOf<DeviceSecurityAuthProvider> {
    DefaultDeviceSecurityAuthProvider()
}

interface DeviceSecurityAuthProvider {
    /**
     * Checks whether the device supports and has enrolled authenticators.
     *
     * @param context Application/Activity context.
     * @param allowDeviceCredential When true, checks for BIOMETRIC_STRONG or DEVICE_CREDENTIAL (PIN/Pattern/Password).
     *                              When false, checks strictly for BIOMETRIC_STRONG.
     * @return True if authentication capability is available, false otherwise.
     */
    fun canAuthenticate(
        context: Context,
        allowDeviceCredential: Boolean = false,
    ): Boolean

    /**
     * Triggers the platform BiometricPrompt authentication flow.
     *
     * @param title Custom prompt title, or defaults to app login title.
     * @param subtitle Custom prompt subtitle, or defaults to app verify identity subtitle.
     * @param allowDeviceCredential When true, enables PIN/Pattern/Password device credential fallback.
     *                              When false, restricts strictly to biometric and shows negative "Close" button.
     * @param onSuccess Callback invoked upon successful authentication.
     * @param onErrorOrCancel Callback invoked upon cancellation or authentication error.
     */
    @Composable
    fun Authenticate(
        title: String? = null,
        subtitle: String? = null,
        allowDeviceCredential: Boolean = false,
        onSuccess: () -> Unit,
        onErrorOrCancel: () -> Unit,
    )
}

class DefaultDeviceSecurityAuthProvider @Inject constructor() : DeviceSecurityAuthProvider {
    override fun canAuthenticate(
        context: Context,
        allowDeviceCredential: Boolean,
    ): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(getAllowedAuthenticators(allowDeviceCredential)) == BiometricManager.BIOMETRIC_SUCCESS
    }

    @Composable
    override fun Authenticate(
        title: String?,
        subtitle: String?,
        allowDeviceCredential: Boolean,
        onSuccess: () -> Unit,
        onErrorOrCancel: () -> Unit,
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

        val defaultTitle = stringResource(R.string.biometric_title_text)
        val defaultSubtitle = stringResource(R.string.biometric_sub_title_text)
        val promptTitle = title ?: defaultTitle
        val promptSubtitle = subtitle ?: defaultSubtitle
        val negativeButtonText = stringResource(R.string.biometric_negative_button_text)
        LaunchedEffect(
            biometricPrompt,
            promptTitle,
            promptSubtitle,
            allowDeviceCredential,
            negativeButtonText,
        ) {
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setAllowedAuthenticators(getAllowedAuthenticators(allowDeviceCredential))

            if (!allowDeviceCredential) {
                promptInfoBuilder.setNegativeButtonText(negativeButtonText)
            }

            biometricPrompt.authenticate(promptInfoBuilder.build())
        }
    }
}
