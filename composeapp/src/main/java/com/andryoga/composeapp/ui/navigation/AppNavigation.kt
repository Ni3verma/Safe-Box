package com.andryoga.composeapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.andryoga.composeapp.ui.LoadingRoute
import com.andryoga.composeapp.ui.home.HomeRoute
import com.andryoga.composeapp.ui.home.HomeScreen
import com.andryoga.composeapp.ui.loading.LoadingScreenRoot
import com.andryoga.composeapp.ui.login.LoginRoute
import com.andryoga.composeapp.ui.login.LoginScreenRoot
import com.andryoga.composeapp.ui.signup.SignupRoute
import com.andryoga.composeapp.ui.signup.SignupScreenRoot
import kotlinx.serialization.Serializable

@Composable
fun AppNavigation(

) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LoginGraph
    ) {
        loginGraph(navController = navController)
        homeGraph(navController = navController)
    }
}

private fun navigateToHome(navController: NavHostController) {
    navController.navigate(HomeGraph) {
        popUpTo(0)
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
                    navController.navigate(LoginRoute) { popUpTo(0) }
                },
                navigateToSignup = {
                    navController.navigate(SignupRoute) { popUpTo(0) }
                }
            )
        }
        composable<LoginRoute> {
            LoginScreenRoot(onLoginSuccess = {
                navigateToHome(navController)
            })
        }

        composable<SignupRoute> {
            SignupScreenRoot(
                onSignupSuccess = {
                    navigateToHome(navController)
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
                    navController.navigate(LoginGraph)
                }
            )
        }
    }
}

@Serializable
object LoginGraph

@Serializable
object HomeGraph