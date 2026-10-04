package com.andryoga.safebox.ui.core

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The single, centralized composable that builds the TopAppBar UI.
 * This ensures all app bars in the app are consistent.
 *
 * Every screen composes it inside its own `Scaffold`, so the bar is part of the destination's
 * content and enters, exits and scrubs (predictive back) together with the screen. It used to be
 * hosted once in `HomeScreen` and configured by whichever screen last reached `ON_START`, which
 * breaks under predictive back because the gesture starts both entries at once; see
 * `docs/decisions/0007-screen-owned-top-app-bars.md`.
 *
 * Marked [ExperimentalMaterial3Api] rather than opted in because [TopAppBarScrollBehavior] is part
 * of the signature; callers have to opt in either way.
 */
@ExperimentalMaterial3Api
@Composable
fun MyAppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}