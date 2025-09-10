package com.andryoga.composeapp.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.home.backupAndRestore.BackupAndRestoreScreen
import com.andryoga.composeapp.ui.home.components.BottomNavBar
import com.andryoga.composeapp.ui.home.navigation.HomeRouteType
import com.andryoga.composeapp.ui.home.records.RecordsScreenRoot
import com.andryoga.composeapp.ui.home.settings.SettingsScreen
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenRoot
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenRoute
import kotlinx.serialization.Serializable

@Composable
fun HomeScreen() {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination

    var showAddNewRecordBottomSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            // Only show the FAB if we are on the HomeRouteType.RecordRoute tab
            if (currentRoute?.route == HomeRouteType.RecordRoute::class.qualifiedName) {
                FloatingActionButton(onClick = { showAddNewRecordBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription =
                            stringResource(R.string.cd_add_new_record_button)
                    )
                }
            }
        },
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
                RecordsScreenRoot(
                    showAddNewRecordBottomSheet = showAddNewRecordBottomSheet,
                    onDismissAddNewRecordBottomSheet = { showAddNewRecordBottomSheet = false },
                    onAddNewRecord = { recordType ->
                        nestedNavController.navigate(route = SingleRecordScreenRoute(recordType))
                    }
                )
            }
            composable<HomeRouteType.BackupAndRestoreRoute> { BackupAndRestoreScreen() }
            composable<HomeRouteType.SettingsRoute> { SettingsScreen() }
            composable<SingleRecordScreenRoute> { SingleRecordScreenRoot() }
        }
    }
}

@Serializable
object HomeRoute
