package com.andryoga.composeapp.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.home.navigation.HomeRouteType

private val items = listOf(
    BottomNavItem.Records,
    BottomNavItem.BackupAndRestore,
    BottomNavItem.Settings
)

@Composable
fun BottomNavBar(nestedNavController: NavHostController, isBackupPathSet: Boolean) {
    NavigationBar {
        val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    // for the backup and restore icon, show a warning badge if backup path is not set
                    if (item == BottomNavItem.BackupAndRestore) {
                        BadgedBox(
                            badge = {
                                if (!isBackupPathSet) {
                                    Badge(content = { Text("!") })
                                }
                            }
                        ) {
                            Icon(item.icon, contentDescription = stringResource(item.titleRes))
                        }
                    } else {
                        Icon(item.icon, contentDescription = stringResource(item.titleRes))
                    }
                },
                label = { Text(stringResource(item.titleRes)) },
                selected = currentDestination?.hasRoute(item.route::class) ?: false,
                onClick = {
                    nestedNavController.navigate(item.route) {
                        popUpTo(nestedNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

sealed class BottomNavItem(
    @param:StringRes var titleRes: Int,
    var icon: ImageVector,
    var route: HomeRouteType
) {
    data object Records : BottomNavItem(
        R.string.bottom_nav_records,
        Icons.AutoMirrored.Filled.List,
        HomeRouteType.RecordRoute
    )

    data object BackupAndRestore : BottomNavItem(
        R.string.bottom_nav_backup_and_restore,
        Icons.Default.SettingsBackupRestore,
        HomeRouteType.BackupAndRestoreRoute()
    )

    data object Settings : BottomNavItem(
        R.string.bottom_nav_settings,
        Icons.Default.Settings,
        HomeRouteType.SettingsRoute
    )
}