package com.andryoga.safebox.ui.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

enum class BiometricableEventType {
    BIOMETRICS_NOT_AVAILABLE,
    BIOMETRICS_AVAILABLE,
    BIOMETRICS_ERROR,
    AUTHENTICATION_FAILED,
    AUTHENTICATION_CANCELED,
    AUTHENTICATION_SUCCEEDED,
}

enum class BiometricsFeatureType {
    BIOMETRICS_FINGERPRINT,
    BIOMETRICS_FACE,
    BIOMETRICS_IRIS,
}

interface Biometricable {
    fun configureBiometrics(
        fragment: Fragment,
        listener: Biometricable? = null,
    )

    fun configureBiometrics(
        activity: FragmentActivity,
        listener: Biometricable? = null,
    )

    fun showBiometricsAuthDialog(
        title: String,
        negativeButtonText: String,
    )

    fun canUseBiometrics(): Boolean

    fun onBiometricEvent(event: BiometricableEventType)

    fun canUseBiometricsFeature(
        feature: BiometricsFeatureType,
        context: Context,
    ): Boolean
}

/**
 * Handler
 */

fun biometricableHandler() =
    object : Biometricable {
        var listener: Biometricable? = null

        val authenticationCallback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        ERROR_HW_UNAVAILABLE,
                        ERROR_UNABLE_TO_PROCESS,
                        ERROR_TIMEOUT,
                        ERROR_NO_SPACE,
                        ERROR_CANCELED,
                        ERROR_LOCKOUT,
                        ERROR_VENDOR,
                        ERROR_LOCKOUT_PERMANENT,
                        ERROR_HW_NOT_PRESENT,
                        -> {
                            /**
                             * A generic error occurred.
                             */
                            listener?.onBiometricEvent(BiometricableEventType.BIOMETRICS_ERROR)
                        }

                        ERROR_NEGATIVE_BUTTON,
                        ERROR_USER_CANCELED,
                        -> {
                            /**
                             * The user pressed the negative button.
                             */
                            listener?.onBiometricEvent(BiometricableEventType.AUTHENTICATION_CANCELED)
                        }

                        ERROR_NO_DEVICE_CREDENTIAL,
                        ERROR_NO_BIOMETRICS,
                        -> {
                            /**
                             * The device does not have pin, pattern, or password set up || the user does
                             * not have any biometrics enrolled.
                             */
                            listener?.onBiometricEvent(BiometricableEventType.BIOMETRICS_NOT_AVAILABLE)
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Authentication succeeded!
                    listener?.onBiometricEvent(BiometricableEventType.AUTHENTICATION_SUCCEEDED)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Authentication failed
                    listener?.onBiometricEvent(BiometricableEventType.AUTHENTICATION_FAILED)
                }
            }

        private lateinit var biometricManager: BiometricManager

        private lateinit var executor: Executor
        private lateinit var biometricPrompt: BiometricPrompt
        private lateinit var promptInfo: PromptInfo

        override fun configureBiometrics(
            fragment: Fragment,
            listener: Biometricable?,
        ) {
            // Get main executor
            this.executor = ContextCompat.getMainExecutor(fragment.requireContext())

            this.biometricPrompt =
                BiometricPrompt(fragment, executor, authenticationCallback)

            this.biometricManager = BiometricManager.from(fragment.requireContext())

            this.listener = listener
        }

        override fun configureBiometrics(
            activity: FragmentActivity,
            listener: Biometricable?,
        ) {
            // Get main executor
            this.executor = ContextCompat.getMainExecutor(activity)

            this.biometricPrompt =
                BiometricPrompt(activity, executor, authenticationCallback)

            this.biometricManager = BiometricManager.from(activity)

            this.listener = listener
        }

        override fun showBiometricsAuthDialog(
            title: String,
            negativeButtonText: String,
        ) {
            // Display the login prompt
            promptInfo =
                PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(negativeButtonText)
                    .build()

            biometricPrompt.authenticate(promptInfo)
        }

        override fun canUseBiometrics(): Boolean {
            return when (biometricManager.canAuthenticate(BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        }

        override fun onBiometricEvent(event: BiometricableEventType) {
            // this should be overridden manually
        }

        override fun canUseBiometricsFeature(
            feature: BiometricsFeatureType,
            context: Context,
        ): Boolean {
            val pm = context.packageManager
            return when (feature) {
                BiometricsFeatureType.BIOMETRICS_FINGERPRINT -> pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                BiometricsFeatureType.BIOMETRICS_FACE -> pm.hasSystemFeature(PackageManager.FEATURE_FACE)
                BiometricsFeatureType.BIOMETRICS_IRIS -> pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
            }
        }
    }
