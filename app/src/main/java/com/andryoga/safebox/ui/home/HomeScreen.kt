package com.andryoga.safebox.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.LocalSnackbarHostState
import com.andryoga.safebox.ui.core.appupdate.InAppUpdateRestartPromptRoot
import com.andryoga.safebox.ui.core.appupdate.InAppUpdateViewModel
import com.andryoga.safebox.ui.home.backupAndRestore.BackupAndRestoreScreenRoot
import com.andryoga.safebox.ui.home.components.BottomNavBar
import com.andryoga.safebox.ui.home.components.UserAwayDialog
import com.andryoga.safebox.ui.home.components.UserAwayDialogRoute
import com.andryoga.safebox.ui.home.navigation.HOME_NAV_TRANSITION_DURATION_MS
import com.andryoga.safebox.ui.home.navigation.HomeRouteType
import com.andryoga.safebox.ui.home.navigation.isHomeTopLevelRoute
import com.andryoga.safebox.ui.home.navigation.rememberHomeNavTransitions
import com.andryoga.safebox.ui.home.records.RecordsScreenRoot
import com.andryoga.safebox.ui.home.settings.SettingsScreenRoot
import com.andryoga.safebox.ui.qrScanner.QrScannerRoute
import com.andryoga.safebox.ui.qrScanner.QrScannerScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoot
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenRoute
import kotlinx.serialization.Serializable
import timber.log.Timber

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
 * Screen-to-screen motion is defined in [rememberHomeNavTransitions]; the bottom bar slides below
 * the screen edge over the same duration when a deeper destination opens, so the content area
 * resizes smoothly instead of jumping when the bar disappears.
 *
 * @param inAppUpdateViewModel: activity-scoped ViewModel shared with the app-level update host,
 * so the restart snackbar reflects the same update state
 * @param onExitHomeNavGraph: this lambda is called when home nav graph will be exited. Clients need
 * to handle this callback and navigate to appropriate screen
 * */
@Composable
fun HomeScreen(
    inAppUpdateViewModel: InAppUpdateViewModel,
    onExitHomeNavGraph: () -> Unit,
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainViewModel = hiltViewModel<MainViewModel>()

    val isBackupPathSet by mainViewModel.isBackupPathSet.collectAsState()
    val isBottomBarVisible = currentDestination.isHomeTopLevelRoute()
    val transitions = rememberHomeNavTransitions()

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
        InAppUpdateRestartPromptRoot(
            viewModel = inAppUpdateViewModel,
            snackbarHostState = globalSnackbarHostState,
        )
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    // The scaffold pins these bounds to the bottom edge, so top-aligning the bar
                    // inside them makes it rise from / sink below the edge while its measured
                    // height animates - which is what keeps the content padding continuous.
                    enter = expandVertically(
                        animationSpec = tween(HOME_NAV_TRANSITION_DURATION_MS),
                        expandFrom = Alignment.Top,
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(HOME_NAV_TRANSITION_DURATION_MS),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
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
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
                predictivePopEnterTransition = { transitions.popEnter(this) },
                predictivePopExitTransition = { transitions.popExit(this) },
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
