package com.andryoga.safebox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    NavAwareInAppUpdateHost(navController = navController)
    NavHost(
        navController = navController,
        startDestination = LoginGraph
    ) {
        loginGraph(navController = navController)
        homeGraph(navController = navController)
    }
}

/**
 * Tells [InAppUpdateHostRoot] whether the root navigation is inside the Home graph.
 *
 * The back stack is read here rather than in [AppNavigation], so a navigation only recomposes
 * this host and never the root `NavHost`.
 */
@Composable
private fun NavAwareInAppUpdateHost(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    InAppUpdateHostRoot(
        isInHomeGraph = backStackEntry?.destination?.hierarchy?.any { it.hasRoute<HomeGraph>() },
    )
}

private fun navigateRoot(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(0) { inclusive = true }
    }
}

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
) {
    navigation<HomeGraph>(startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
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