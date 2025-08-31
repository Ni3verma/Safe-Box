package com.andryoga.composeapp.ui.home.navigation

import kotlinx.serialization.Serializable

sealed interface HomeRouteType {
    @Serializable
    object RecordRoute : HomeRouteType

    @Serializable
    object BackupAndRestoreRoute : HomeRouteType

    @Serializable
    object SettingsRoute : HomeRouteType
}