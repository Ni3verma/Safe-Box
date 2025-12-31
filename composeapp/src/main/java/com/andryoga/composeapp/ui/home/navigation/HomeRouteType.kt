package com.andryoga.composeapp.ui.home.navigation

import kotlinx.serialization.Serializable

/*
* These are the routes that will be used in the bottom navigation bar.
* */
sealed interface HomeRouteType {
    @Serializable
    object RecordRoute : HomeRouteType

    @Serializable
    data class BackupAndRestoreRoute(
        /**
         * If restore workflow should be started immediately when backup and restore screen is opened.
         * e.g. use case: when user has installed app for the first time, he has no record.
         * On the records screen, he has an option to restore from  backup file.
         * */
        val startWithRestoreWorkflow: Boolean = false
    ) : HomeRouteType

    @Serializable
    object SettingsRoute : HomeRouteType
}
