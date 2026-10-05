package com.andryoga.safebox.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.andryoga.safebox.ui.LoadingRoute
import com.andryoga.safebox.ui.core.appupdate.InAppUpdateHostRoot
import com.andryoga.safebox.ui.core.appupdate.InAppUpdateViewModel
import com.andryoga.safebox.ui.core.motion.fadeOverEnter
import com.andryoga.safebox.ui.core.motion.fadeThroughEnter
import com.andryoga.safebox.ui.core.motion.fadeThroughExit
import com.andryoga.safebox.ui.home.HomeRoute
import com.andryoga.safebox.ui.home.HomeScreen
import com.andryoga.safebox.ui.loading.LoadingScreenRoot
import com.andryoga.safebox.ui.login.LoginRoute
import com.andryoga.safebox.ui.login.LoginScreenRoot
import com.andryoga.safebox.ui.signup.SignupRoute
import com.andryoga.safebox.ui.signup.SignupScreenRoot
import kotlinx.serialization.Serializable

@Composable
fun AppNavigation(

) {
    val navController = rememberNavController()
    // Obtained outside any NavHost, so it is activity-scoped. The update host and Home's restart
    // snackbar must observe the same instance.
    val inAppUpdateViewModel = hiltViewModel<InAppUpdateViewModel>()

    NavAwareInAppUpdateHost(
        navController = navController,
        inAppUpdateViewModel = inAppUpdateViewModel,
    )
    // Every root hop replaces the whole back stack, so push and pop look the same: a fade through.
    // Loading and the auth screens paint the same gradient, so that hop instead fades the new
    // screen in over a Loading screen that is held in place: a sequential fade through would dip
    // to a blank frame between two identical backgrounds, whereas this keeps the gradient solid
    // and leaves the form's own entrance (see AuthScreenLayout) as the only motion the user sees.
    NavHost(
        navController = navController,
        startDestination = LoginGraph,
        enterTransition = {
            if (isLeavingLoading()) fadeOverEnter() else fadeThroughEnter()
        },
        exitTransition = {
            if (isLeavingLoading()) ExitTransition.KeepUntilTransitionsFinished else fadeThroughExit()
        },
        popEnterTransition = { fadeThroughEnter() },
        popExitTransition = { fadeThroughExit() },
    ) {
        loginGraph(navController = navController)
        homeGraph(
            navController = navController,
            inAppUpdateViewModel = inAppUpdateViewModel,
        )
    }
}

/**
 * Tells [InAppUpdateHostRoot] whether the root navigation is inside the Home graph.
 *
 * The back stack is read here rather than in [AppNavigation], so a navigation only recomposes
 * this host and never the root `NavHost`.
 */
@Composable
private fun NavAwareInAppUpdateHost(
    navController: NavHostController,
    inAppUpdateViewModel: InAppUpdateViewModel,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    InAppUpdateHostRoot(
        viewModel = inAppUpdateViewModel,
        isInHomeGraph = backStackEntry?.destination?.hierarchy?.any { it.hasRoute<HomeGraph>() },
    )
}

private fun navigateRoot(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(0) { inclusive = true }
    }
}

/** True while the root `NavHost` is animating away from the Loading screen. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLeavingLoading(): Boolean =
    initialState.destination.hasRoute<LoadingRoute>()

private fun NavGraphBuilder.loginGraph(
    navController: NavHostController
) {
    navigation<LoginGraph>(startDestination = LoadingRoute) {
        composable<LoadingRoute> {
            // this composable will not be shown for a long time as we will
            // get either go to login or signup screen. and on fast devices this is not even visible.
            LoadingScreenRoot(
                navigateToLogin = {
                    navigateRoot(navController, LoginRoute)
                },
                navigateToSignup = {
                    navigateRoot(navController, SignupRoute)
                }
            )
        }
        composable<LoginRoute> {
            LoginScreenRoot(onLoginSuccess = {
                navigateRoot(navController, HomeGraph)
            })
        }

        composable<SignupRoute> {
            SignupScreenRoot(
                onSignupSuccess = {
                    navigateRoot(navController, HomeGraph)
                }
            )
        }
    }
}

private fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    inAppUpdateViewModel: InAppUpdateViewModel,
) {
    navigation<HomeGraph>(startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                inAppUpdateViewModel = inAppUpdateViewModel,
                onExitHomeNavGraph = {
                    navigateRoot(navController, LoginGraph)
                }
            )
        }
    }
}

@Serializable
object LoginGraph

@Serializable
object HomeGraph