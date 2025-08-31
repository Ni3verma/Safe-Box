package com.andryoga.composeapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andryoga.composeapp.ui.LoadingRoute
import com.andryoga.composeapp.ui.LoadingScreen
import com.andryoga.composeapp.ui.StartDestination
import com.andryoga.composeapp.ui.home.HomeRoute
import com.andryoga.composeapp.ui.home.HomeScreen
import com.andryoga.composeapp.ui.login.LoginRoute
import com.andryoga.composeapp.ui.login.LoginScreenRoot
import com.andryoga.composeapp.ui.signup.SignupRoute
import com.andryoga.composeapp.ui.signup.SignupScreenRoot

@Composable
fun AppNavigation(startDestinationState: StartDestination) {
    val navController = rememberNavController()
    val startDestination = when (startDestinationState) {
        StartDestination.Loading -> LoadingRoute
        StartDestination.Login -> LoginRoute
        StartDestination.Signup -> SignupRoute
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<LoadingRoute> {
            // this composable will not be shown for a long time as we will
            // get either go to login or signup screen. and on fast devices this is not even visible.
            LoadingScreen()
        }
        composable<LoginRoute> {
            LoginScreenRoot(onLoginSuccess = {
                navigateToHome(navController)
            })
        }

        composable<SignupRoute> {
            SignupScreenRoot()
        }

        composable<HomeRoute> {
            HomeScreen()
        }
    }
}

private fun navigateToHome(navController: NavHostController) {
    navController.navigate(HomeRoute) {
        popUpTo(LoginRoute) {
            inclusive = true
        }
    }
}