package com.andryoga.safebox.ui.common

data class NotificationOptions(
    val channelId: String,
    val notificationId: Int,
    val channelName: String,
    val channelDescription: String,
    val channelImportance: Int,
    val notificationSmallIcon: Int,
    val notificationTitle: String,
    val notificationContent: String,
    val notificationPriority: Int
)
