package com.andryoga.safebox.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.domain.models.NotificationOptions
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Utils {

    fun String?.encryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.encrypt(this)
    }

    fun String?.decryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.decrypt(this)
    }

    /**
     * The fail-closed contract of every secure-DAO `Flow`: a vault that is locked while the list
     * is being decrypted yields an empty list instead of an exception, so a `stateIn` pipeline in
     * a ViewModel survives a lock without crashing. Anything else still propagates; a broken row
     * is a bug to surface, not a state to hide. The flow completes after the empty emission; the
     * next unlock navigates to a fresh Home, whose ViewModel subscribes again.
     */
    fun <T> Flow<List<T>>.emptyWhenSessionLocked(): Flow<List<T>> = catch { cause ->
        if (cause is SessionLockedException) emit(emptyList()) else throw cause
    }

    /**
     * The launch counterpart of [emptyWhenSessionLocked], for the suspend calls a ViewModel makes
     * against the vault: a `SessionLockedException` ends the operation silently, because the lock
     * that caused it is already taking the user to Login, and anything else propagates as before.
     * Every `viewModelScope.launch` that reads or writes records goes through this.
     */
    fun CoroutineScope.launchVaultOperation(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = launch(context) {
        try {
            block()
        } catch (_: SessionLockedException) {
            Timber.i("vault operation abandoned: the vault is locked")
        }
    }

    /**
     * returns true if integer is non null and equal to 0, false otherwise
     */
    fun Int?.isZero(): Boolean = (this ?: 0) == 0

    fun getFormattedDate(date: Date, pattern: String = "EEEE, dd MMM yyyy hh:mm a"): String {
        return SimpleDateFormat(pattern, Locale.US).format(date)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun makeStatusNotification(context: Context, notificationOptions: NotificationOptions) {
        // Channels are mandatory on every supported API level; createNotificationChannel is a no-op
        // for an existing channel, so this is safe to repeat before every notification.
        val channel = NotificationChannel(
            notificationOptions.channelId,
            notificationOptions.channelName,
            notificationOptions.channelImportance,
        )
        channel.description = notificationOptions.channelDescription

        // Add the channel
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

        notificationManager?.createNotificationChannel(channel)

        // Create the notification
        val builder = NotificationCompat.Builder(context, notificationOptions.channelId)
            .setSmallIcon(notificationOptions.notificationSmallIcon)
            .setContentTitle(notificationOptions.notificationTitle)
            .setContentText(notificationOptions.notificationContent)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(notificationOptions.notificationContent)
            )
            .setPriority(notificationOptions.notificationPriority)
            .setVibrate(LongArray(0))

        // Show the notification
        NotificationManagerCompat.from(context)
            .notify(notificationOptions.notificationId, builder.build())
    }

    /**
     * Use this in all the scenarios which should not happen.
     * this method will crash the app in debug builds and give us early signal that something is wrong.
     *
     * @param cause The exception that exposed the impossible state, if any. In release builds it
     * reaches Crashlytics as a non-fatal through [Timber.e]; without it only the message is logged.
     * */
    fun crashInDebugBuild(errorMessage: String, cause: Throwable? = null) {
        val message = "Ooooppsss, this should not have happened," +
                " Note that this is a debug build exclusive crash but this must be addressed !! \n" +
                " Error: $errorMessage"
        Timber.e(cause, message)

        if (BuildConfig.DEBUG) {
            throw Exceptions.DebugFatalException(message).apply { cause?.let { initCause(it) } }
        }
    }
}
