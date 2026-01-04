package com.andryoga.safebox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.andryoga.safebox.ui.LoadingRoute
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

    NavHost(
        navController = navController,
        startDestination = HomeGraph //todo
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