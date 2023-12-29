package com.andryoga.safebox.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.setCustomKeys
import timber.log.Timber

class CrashlyticsKeys(
    private val context: Context,
) {
    fun setDefaultKeys() {
        Timber.i("setting crashlytics keys")
        FirebaseCrashlytics.getInstance().setCustomKeys {
            key("locale", locale)
            key("Screen Density", density)
            key("Google Play Services Availability", googlePlayServicesAvailability)
            key("Os Version", osVersion)
            key("Install Source", installSource)
            key("Preferred ABI", preferredAbi)
        }
    }

    /**
     * Retrieve the locale information for the app.
     *
     * Suppressed deprecation warning because that code path is only used below API Level N.
     */
    @Suppress("DEPRECATION")
    private val locale: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context
                    .resources
                    .configuration
                    .locales[0].toString()
            } else {
                context
                    .resources
                    .configuration.locale.toString()
            }

    /**
     * Retrieve the screen density information for the app.
     */
    private val density: Float
        get() =
            context
                .resources
                .displayMetrics.density

    /**
     * Retrieve the locale information for the app.
     */
    private val googlePlayServicesAvailability: String
        get() =
            if (GoogleApiAvailabilityLight
                    .getInstance()
                    .isGooglePlayServicesAvailable(context) == 0
            ) {
                "Unavailable"
            } else {
                "Available"
            }

    /**
     * Return the underlying kernel version of the Android device.
     */
    private val osVersion: String
        get() = System.getProperty("os.version") ?: "Unknown"

    /**
     * Retrieve the preferred ABI of the device. Some devices can support
     * multiple ABIs and the first one returned in the preferred one.
     */
    private val preferredAbi: String
        get() =
            Build.SUPPORTED_ABIS[0]

    /**
     * Retrieve the install source and return it as a string.
     *
     * Suppressed deprecation warning because that code path is only used below API level R.
     */
    @Suppress("DEPRECATION")
    private val installSource: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val info =
                        context
                            .packageManager
                            .getInstallSourceInfo(context.packageName)

                    // This returns all three of the install source, originating source, and initiating
                    // source.
                    "Originating: ${info.originatingPackageName ?: "None"}, " +
                        "Installing: ${info.installingPackageName ?: "None"}, " +
                        "Initiating: ${info.initiatingPackageName ?: "None"}"
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.e(e)
                    "Unknown"
                }
            } else {
                context.packageManager.getInstallerPackageName(context.packageName) ?: "None"
            }
}
