package com.andryoga.safebox.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.LocalSnackbarHostState
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.core.ScrollBehaviorType
import com.andryoga.safebox.ui.core.TopBarState
import com.andryoga.safebox.ui.home.backupAndRestore.BackupAndRestoreScreenRoot
import com.andryoga.safebox.ui.home.components.BottomNavBar
import com.andryoga.safebox.ui.home.components.UserAwayDialog
import com.andryoga.safebox.ui.home.components.UserAwayDialogRoute
import com.andryoga.safebox.ui.home.navigation.HomeRouteType
import com.andryoga.safebox.ui.home.records.RecordsScreenRoot
import com.andryoga.safebox.ui.home.settings.SettingsScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoute
import kotlinx.serialization.Serializable

/**
 * This is home Nav graph container with Records screen as the start destination.
 * @param onExitHomeNavGraph: this lambda is called when home nav graph will be exited. Clients need
 * to handle this callback and navigate to appropriate screen
 * */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExitHomeNavGraph: () -> Unit,
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainViewModel = hiltViewModel<MainViewModel>()

    val enterAlwaysScrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    val exitUntilCollapsedScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val topBarState by mainViewModel.topBarState.collectAsState()
    val currentConfig = (topBarState as? TopBarState.Visible)?.config

    val isBackupPathSet by mainViewModel.isBackupPathSet.collectAsState()

    val globalSnackbarHostState = remember { SnackbarHostState() }

    // Use a 'when' block to select the BEHAVIOR and its CONNECTION for the current screen.
    val (scrollBehavior, nestedScrollConnection) = when (currentConfig?.scrollBehaviorType) {
        ScrollBehaviorType.ENTER_ALWAYS -> enterAlwaysScrollBehavior to enterAlwaysScrollBehavior.nestedScrollConnection
        ScrollBehaviorType.EXIT_UNTIL_COLLAPSED -> exitUntilCollapsedScrollBehavior to exitUntilCollapsedScrollBehavior.nestedScrollConnection
        else -> null to object : NestedScrollConnection {} // For NONE or when hidden
    }

    LaunchedEffect(Unit) {
        mainViewModel.logoutEvent.collect {
            nestedNavController.navigate(UserAwayDialogRoute)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides globalSnackbarHostState) {
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
                if (isUserOnHomeRouteScreen(currentDestination)) {
                    BottomNavBar(nestedNavController, isBackupPathSet)
                }
            },
            snackbarHost = { SnackbarHost(globalSnackbarHostState) },
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
                        onRestoreFromBackup = {
                            nestedNavController.navigate(
                                route = HomeRouteType.BackupAndRestoreRoute(
                                    startWithRestoreWorkflow = true
                                )
                            ) {
                                popUpTo(nestedNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
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
                    SettingsScreenRoot(
                        mainViewModel = mainViewModel
                    )
                }
                composable<SingleRecordScreenRoute> {
                    SingleRecordScreenRoot(
                        mainViewModel = mainViewModel,
                        onScreenClose = {
                            nestedNavController.popBackStack()
                        }
                    )
                }
                composable<UserAwayDialogRoute> {
                    UserAwayDialog(onExitHomeNavGraph = onExitHomeNavGraph)
                }
            }
        }
    }
}

private fun isUserOnHomeRouteScreen(currentDestination: NavDestination?): Boolean {
    return currentDestination?.run {
        hasRoute<HomeRouteType.RecordRoute>() || hasRoute<HomeRouteType.BackupAndRestoreRoute>() ||
                hasRoute<HomeRouteType.SettingsRoute>()
    } ?: false
}

@Serializable
object HomeRoute
