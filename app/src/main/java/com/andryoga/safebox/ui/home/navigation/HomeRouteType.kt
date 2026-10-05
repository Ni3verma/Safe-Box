package com.andryoga.safebox.ui.home.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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

/**
 * Whether this destination is one of the three bottom-navigation tabs.
 *
 * Everything else in the home graph (a record's detail or create screen, the QR scanner) is a
 * level deeper: it hides the bottom bar and is reached with the hierarchical transition.
 */
fun NavDestination?.isHomeTopLevelRoute(): Boolean =
    this?.run {
        hasRoute<HomeRouteType.RecordRoute>() ||
            hasRoute<HomeRouteType.BackupAndRestoreRoute>() ||
            hasRoute<HomeRouteType.SettingsRoute>()
    } ?: false
