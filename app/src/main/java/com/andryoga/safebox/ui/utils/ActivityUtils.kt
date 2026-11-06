package com.andryoga.safebox.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import timber.log.Timber

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Permissions should be requested from an Activity context.")
}

/**
 * Opens this app's system settings page, the only place a permanently denied runtime permission
 * can be restored from.
 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

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

/**
 * Opens [url] via [Intent.ACTION_VIEW]. `https` is declared in `<queries>`; `market://` resolves
 * because `com.android.vending` is force-queryable.
 *
 * @param url External URI string to open.
 * @return `true` if an activity handled the intent, `false` if none could resolve it.
 */
fun Context.openExternalUrl(url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    if (intent.resolveActivity(packageManager) == null) return false
    startActivity(intent)
    return true
}