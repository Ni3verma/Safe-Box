package com.andryoga.safebox.ui.core

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
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
import timber.log.Timber
import javax.inject.Inject

/**
 * Resolves the [BiometricManager.Authenticators] bit set used for both the capability check and
 * the prompt, so the two can never disagree.
 *
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is documented by androidx.biometric as **unsupported on
 * API 28-29** (see `PromptInfo.Builder.setAllowedAuthenticators`): Android 9/10 have no biometric
 * strength classes in their API, and once a device-credential fallback is allowed the library can
 * no longer force strong-only behaviour, so it refuses the combination outright —
 * `BiometricManager.canAuthenticate` returns `BIOMETRIC_ERROR_UNSUPPORTED` regardless of enrolment
 * and `PromptInfo.Builder.build()` throws. Below API 30 the credential flows therefore request
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; the sensor shown to the user is the same, and those flows
 * already accept a PIN, which is weaker than any biometric. API 30+ keeps `BIOMETRIC_STRONG` with
 * the credential so a Class-2 face unlock is not newly admitted there, and biometric-only login keeps
 * `BIOMETRIC_STRONG` on every version. See issue #279.
 *
 * @param allowDeviceCredential Whether PIN/pattern/password may be used instead of a biometric.
 * @param sdkInt The running API level; injectable only so the matrix is unit-testable on the JVM.
 * @return The authenticator bit set to pass to [BiometricManager.canAuthenticate] and
 *   [BiometricPrompt.PromptInfo.Builder.setAllowedAuthenticators].
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun getAllowedAuthenticators(
    allowDeviceCredential: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Int = when {
    !allowDeviceCredential -> BiometricManager.Authenticators.BIOMETRIC_STRONG
    sdkInt < Build.VERSION_CODES.R ->
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    else ->
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
        val result = BiometricManager.from(context)
            .canAuthenticate(getAllowedAuthenticators(allowDeviceCredential))
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            // Keep the raw code: BIOMETRIC_ERROR_UNSUPPORTED (bad authenticator combination) and
            // BIOMETRIC_ERROR_NONE_ENROLLED (no lock screen) must stay distinguishable in logs.
            Timber.w("canAuthenticate(allowDeviceCredential=$allowDeviceCredential) returned $result")
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
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
