package com.andryoga.composeapp.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.NotificationOptions
import com.andryoga.safebox.ui.common.Resource
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date

object Utils {
    fun logResource(tag: String, resource: Resource<Any>) {
        Timber.d("$tag --> status = ${resource.status}\ndata = ${resource.data}\nmessage = ${resource.message}\n")
    }

    fun String?.encryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.encrypt(this)
    }

    fun String?.decryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.decrypt(this)
    }

    fun getFormattedDate(date: Date, pattern: String = "EEEE, dd MMM yyyy hh:mm a"): String {
        return SimpleDateFormat(pattern).format(date)
    }

    fun makeStatusNotification(context: Context, notificationOptions: NotificationOptions) {
        // Make a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            val channel = NotificationChannel(
                notificationOptions.channelId,
                notificationOptions.channelName,
                notificationOptions.channelImportance
            )
            channel.description = notificationOptions.channelDescription

            // Add the channel
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

            notificationManager?.createNotificationChannel(channel)
        }

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
}
