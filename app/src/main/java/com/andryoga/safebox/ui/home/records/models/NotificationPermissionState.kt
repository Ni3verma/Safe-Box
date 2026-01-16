package com.andryoga.safebox.ui.home.records.models

data class NotificationPermissionState(
    val isNotificationPermissionAskedBefore: Boolean = false,
    val isNeverAskForNotificationPermission: Boolean = true,
    val isBackupPathSet: Boolean = true
)
