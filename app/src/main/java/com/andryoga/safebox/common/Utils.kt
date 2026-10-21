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
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    fun String?.encryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.encrypt(this)
    }

    fun String?.decryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.decrypt(this)
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
