package com.andryoga.composeapp.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.andryoga.composeapp.BuildConfig
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.NotificationOptions
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

    /**
     * returns true if integer is non null and greater than 0, false otherwise
     */
    fun Int?.isPositive(): Boolean = (this ?: 0) > 0

    /**
     * returns true if integer is non null and equal to 0, false otherwise
     */
    fun Int?.isZero(): Boolean = (this ?: 0) == 0

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

    fun longestCommonSubstring(string1: String, string2: String): Int {
        val matrix = Array(string1.length + 1) {
            IntArray(string2.length + 1)
        }
        var maxLength = 0
        for (i in 1 until matrix.size) {
            for (j in 1 until matrix[0].size) {
                val text1 = string1[i - 1]
                val text2 = string2[j - 1]
                if (text1 != text2) {
                    matrix[i][j] = 0
                } else {
                    matrix[i][j] = matrix[i - 1][j - 1] + 1
                }
                if (matrix[i][j] > maxLength) {
                    maxLength = matrix[i][j]
                }
            }
        }
        return maxLength
    }

    /**
     * Use this in all the scenarios which should not happen.
     * this method will crash the app in debug builds and give us early signal that something is wrong.
     * */
    fun crashInDebugBuild(errorMessage: String) {
        val message = "Ooooppsss, this should not have happened," +
                " Note that this is a debug build exclusive crash but this must be addressed !! \n" +
                " Error: $errorMessage"
        Timber.e(message)

        if (BuildConfig.DEBUG) {
            throw Exceptions.DebugFatalException(message)
        }
    }
}
