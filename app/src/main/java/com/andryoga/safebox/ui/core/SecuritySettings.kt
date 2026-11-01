package com.andryoga.safebox.ui.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import timber.log.Timber

/**
 * Opens the system screen where a lock screen is set up: `ACTION_SECURITY_SETTINGS`, falling back
 * to the Settings root on devices whose OEM build does not export the security screen.
 *
 * @return `true` when one of the two activities was launched, `false` when both failed.
 */
fun Context.openSecuritySettings(): Boolean = runCatching {
    startActivity(
        Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}.recoverCatching { securitySettingsError ->
    Timber.w(
        securitySettingsError,
        "Failed to open ACTION_SECURITY_SETTINGS, attempting fallback to ACTION_SETTINGS"
    )
    startActivity(
        Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}.onFailure { fallbackError ->
    Timber.e(fallbackError, "Failed to launch the system security settings")
}.isSuccess
