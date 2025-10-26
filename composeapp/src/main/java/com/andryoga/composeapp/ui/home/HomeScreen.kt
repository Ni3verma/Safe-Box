package com.andryoga.composeapp.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andryoga.composeapp.ui.MainViewModel
import com.andryoga.composeapp.ui.core.MyAppTopAppBar
import com.andryoga.composeapp.ui.core.ScrollBehaviorType
import com.andryoga.composeapp.ui.core.TopBarState
import com.andryoga.composeapp.ui.home.backupAndRestore.BackupAndRestoreScreenRoot
import com.andryoga.composeapp.ui.home.components.BottomNavBar
import com.andryoga.composeapp.ui.home.navigation.HomeRouteType
import com.andryoga.composeapp.ui.home.records.RecordsScreenRoot
import com.andryoga.composeapp.ui.home.settings.SettingsScreen
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenRoot
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenRoute
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination
    val mainViewModel = hiltViewModel<MainViewModel>()

    val enterAlwaysScrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    val exitUntilCollapsedScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val topBarState by mainViewModel.topBarState.collectAsState()
    val currentConfig = (topBarState as? TopBarState.Visible)?.config

    // Use a 'when' block to select the BEHAVIOR and its CONNECTION for the current screen.
    val (scrollBehavior, nestedScrollConnection) = when (currentConfig?.scrollBehaviorType) {
        ScrollBehaviorType.ENTER_ALWAYS -> enterAlwaysScrollBehavior to enterAlwaysScrollBehavior.nestedScrollConnection
        ScrollBehaviorType.EXIT_UNTIL_COLLAPSED -> exitUntilCollapsedScrollBehavior to exitUntilCollapsedScrollBehavior.nestedScrollConnection
        else -> null to object : NestedScrollConnection {} // For NONE or when hidden
    }

    Scaffold(
        topBar = {
            if (currentConfig != null) {
                MyAppTopAppBar(
                    config = currentConfig,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = {
            if (isUserOnHomeRouteScreen(currentRoute?.route)) {
                BottomNavBar(nestedNavController)
            }
        },
        modifier = Modifier.nestedScroll(nestedScrollConnection)
    ) { innerPadding ->
        NavHost(
            navController = nestedNavController,
            startDestination = HomeRouteType.RecordRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRouteType.RecordRoute> {
                RecordsScreenRoot(
                    mainViewModel = mainViewModel,
                    onAddNewRecord = { recordType ->
                        nestedNavController.navigate(route = SingleRecordScreenRoute(recordType))
                    },
                    onRecordClick = { id, recordType ->
                        nestedNavController.navigate(
                            route = SingleRecordScreenRoute(
                                recordType,
                                id
                            )
                        )
                    },
                )
            }
            composable<HomeRouteType.BackupAndRestoreRoute> {
                BackupAndRestoreScreenRoot(
                    mainViewModel = mainViewModel
                )
            }
            composable<HomeRouteType.SettingsRoute> {
                SettingsScreen()
            }
            composable<SingleRecordScreenRoute> {
                SingleRecordScreenRoot(
                    mainViewModel = mainViewModel,
                    onScreenClose = {
                        nestedNavController.popBackStack()
                    }
                )
            }
        }
    }
}

private fun isUserOnHomeRouteScreen(route: String?): Boolean {
    return HomeRouteType.RecordRoute::class.qualifiedName == route ||
            HomeRouteType.BackupAndRestoreRoute::class.qualifiedName == route ||
            HomeRouteType.SettingsRoute::class.qualifiedName == route
}

@Serializable
object HomeRoute
