package com.andryoga.composeapp.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andryoga.composeapp.ui.home.backupAndRestore.BackupAndRestoreScreen
import com.andryoga.composeapp.ui.home.components.BottomNavBar
import com.andryoga.composeapp.ui.home.navigation.HomeRouteType
import com.andryoga.composeapp.ui.home.records.RecordsScreenRoot
import com.andryoga.composeapp.ui.home.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Composable
fun HomeScreen() {
    val nestedNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavBar(nestedNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController = nestedNavController,
            startDestination = HomeRouteType.RecordRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRouteType.RecordRoute> {
                RecordsScreenRoot()
            }
            composable<HomeRouteType.BackupAndRestoreRoute> { BackupAndRestoreScreen() }
            composable<HomeRouteType.SettingsRoute> { SettingsScreen() }
        }
    }
}

@Serializable
object HomeRoute
