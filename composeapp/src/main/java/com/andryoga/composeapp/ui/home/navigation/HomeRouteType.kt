package com.andryoga.composeapp.ui.home.navigation

import kotlinx.serialization.Serializable

/*
* These are the routes that will be used in the bottom navigation bar.
* */
sealed interface HomeRouteType {
    @Serializable
    object RecordRoute : HomeRouteType

    @Serializable
    object BackupAndRestoreRoute : HomeRouteType

    @Serializable
    object SettingsRoute : HomeRouteType
}
