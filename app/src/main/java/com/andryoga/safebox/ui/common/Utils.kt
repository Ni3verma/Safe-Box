package com.andryoga.safebox.ui.common

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.databinding.ObservableField

object Utils {
    fun switchVisibility(vararg views: View) {
        views.forEach { view ->
            when (view.visibility) {
                View.VISIBLE -> {
                    view.visibility = View.INVISIBLE
                }
                View.INVISIBLE -> {
                    view.visibility = View.VISIBLE
                }
                View.GONE -> {
                    view.visibility = View.VISIBLE
                }
            }
        }
    }

    fun hideSoftKeyboard(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (activity.currentFocus != null) {
            imm.hideSoftInputFromWindow(activity.currentFocus!!.windowToken, 0)
        }
    }

    fun startMotionLayoutTransition(
        motionLayout: MotionLayout,
        endState: Int,
        startState: Int = motionLayout.currentState,
        duration: Int = 500
    ) {
        if (motionLayout.currentState != endState) {
            motionLayout.setTransition(startState, endState)
            motionLayout.setTransitionDuration(duration)
            motionLayout.transitionToEnd()
        }
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

    /* This is an extension function to get observable field data
    * Use it only if you know that data will never be null
    * instead of using !!, use this util function*/
    fun ObservableField<String>.getValueOrEmpty(): String {
        return this.get() ?: ""
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
            .setPriority(notificationOptions.notificationPriority)
            .setVibrate(LongArray(0))

        // Show the notification
        NotificationManagerCompat.from(context)
            .notify(notificationOptions.notificationId, builder.build())
    }
}
