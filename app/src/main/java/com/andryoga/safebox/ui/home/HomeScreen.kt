package com.andryoga.safebox.ui.home

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.LocalSnackbarHostState
import com.andryoga.safebox.ui.home.backupAndRestore.BackupAndRestoreScreenRoot
import com.andryoga.safebox.ui.home.components.BottomNavBar
import com.andryoga.safebox.ui.home.components.UserAwayDialog
import com.andryoga.safebox.ui.home.components.UserAwayDialogRoute
import com.andryoga.safebox.ui.home.navigation.HomeRouteType
import com.andryoga.safebox.ui.home.records.RecordsScreenRoot
import com.andryoga.safebox.ui.home.settings.SettingsScreenRoot
import com.andryoga.safebox.ui.qrScanner.QrScannerRoute
import com.andryoga.safebox.ui.qrScanner.QrScannerScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoute
import kotlinx.serialization.Serializable
import timber.log.Timber

private const val NAV_TRANSITION_DURATION_MS = 700

/**
 * Every navigation inside the home graph is a crossfade, and a predictive back gesture scrubs
 * that same crossfade instead of the library's own gesture animation.
 *
 * Navigation Compose 2.10 gave the gesture separate defaults (`scaleOut(0.7f)` for the leaving
 * screen, a spring fade for the returning one), so a `NavHost` that only pins the four button-driven
 * slots still changes look whenever the library's defaults do. All six slots are pinned here so the
 * two back paths cannot drift apart again.
 */
private val homeEnterTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) }

private val homeExitTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) }

/**
 * This is home Nav graph container with Records screen as the start destination.
 *
 * The scaffold here owns only what is shared across destinations: the bottom navigation bar and
 * the global snackbar host. Each destination composes its own `Scaffold` with its own top app bar,
 * so the bar is part of the destination content and animates (including predictive back) with it.
 * Window insets are therefore left to the destinations: this scaffold passes none down and the
 * `NavHost` consumes whatever the bottom bar occupies, so a destination's own scaffold or inset
 * modifiers see only what is still unhandled.
 *
 * @param onExitHomeNavGraph: this lambda is called when home nav graph will be exited. Clients need
 * to handle this callback and navigate to appropriate screen
 * */
@Composable
fun HomeScreen(
    onExitHomeNavGraph: () -> Unit,
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainViewModel = hiltViewModel<MainViewModel>()

    val isBackupPathSet by mainViewModel.isBackupPathSet.collectAsState()
    val isBottomBarVisible = isUserOnHomeRouteScreen(currentDestination)

    val globalSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(nestedNavController) {
        nestedNavController.currentBackStackEntryFlow.collect { backStackEntry ->
            val screenName = backStackEntry.destination.route
                ?.substringAfterLast('.')
                ?.substringBefore("?")
                ?.substringBefore("/")

            if (screenName != null) {
                Timber.i("Navigated to: $screenName")
            }
        }
    }

    LaunchedEffect(mainViewModel.logoutEvent) {
        mainViewModel.logoutEvent.collect {
            nestedNavController.navigate(UserAwayDialogRoute)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides globalSnackbarHostState) {
        Scaffold(
            bottomBar = {
                if (isBottomBarVisible) {
                    BottomNavBar(nestedNavController, isBackupPathSet)
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = globalSnackbarHostState,
                    // Scaffold lifts the snackbar above the bottom bar; without one it would sit
                    // under the system navigation bar because this scaffold handles no insets.
                    modifier = if (isBottomBarVisible) Modifier else Modifier.navigationBarsPadding(),
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            NavHost(
                navController = nestedNavController,
                startDestination = HomeRouteType.RecordRoute,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                enterTransition = homeEnterTransition,
                exitTransition = homeExitTransition,
                popEnterTransition = homeEnterTransition,
                popExitTransition = homeExitTransition,
                predictivePopEnterTransition = { homeEnterTransition() },
                predictivePopExitTransition = { homeExitTransition() },
            ) {
                composable<HomeRouteType.RecordRoute> {
                    RecordsScreenRoot(
                        onAddNewRecord = { recordType ->
                            if (recordType == RecordType.AUTHENTICATOR) {
                                nestedNavController.navigate(route = QrScannerRoute)
                            } else {
                                nestedNavController.navigate(
                                    route = SingleRecordScreenRoute(recordType)
                                )
                            }
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
                    BackupAndRestoreScreenRoot()
                }
                composable<HomeRouteType.SettingsRoute> {
                    SettingsScreenRoot()
                }
                composable<QrScannerRoute> {
                    QrScannerScreenRoot(
                        onQrCodeScanned = {
                            nestedNavController.navigateToNewAuthenticatorRecord()
                        },
                        onEnterKeyManually = {
                            nestedNavController.navigateToNewAuthenticatorRecord()
                        },
                        onClose = {
                            nestedNavController.popBackStack()
                        },
                    )
                }
                composable<SingleRecordScreenRoute> {
                    SingleRecordScreenRoot(
                        onScreenClose = {
                            Timber.i("single record screen closure callback")
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

/**
 * Opens the create screen for a new authenticator record and drops the scanner from the back stack.
 *
 * Both scanner exits that create a record use this, so pressing back from the create screen
 * returns to the records list rather than reopening the camera on a code that was already read.
 *
 * `launchSingleTop` covers a double tap on the manual entry button: the second navigation finds
 * the scanner already popped, so `popUpTo` alone would let it stack a duplicate create screen.
 */
private fun NavHostController.navigateToNewAuthenticatorRecord() {
    navigate(route = SingleRecordScreenRoute(RecordType.AUTHENTICATOR)) {
        popUpTo<QrScannerRoute> { inclusive = true }
        launchSingleTop = true
    }
}

@Serializable
object HomeRoute
