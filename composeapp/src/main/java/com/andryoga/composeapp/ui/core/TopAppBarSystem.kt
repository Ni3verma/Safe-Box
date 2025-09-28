package com.andryoga.composeapp.ui.core

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

/**
 * Defines the types of scroll behavior a screen's TopAppBar can have.
 */
enum class ScrollBehaviorType {
    /** The TopAppBar is fixed and does not react to scrolling. */
    NONE,

    /** A simple scroll-away behavior that reappears when scrolling down. */
    ENTER_ALWAYS,

    /** A collapsing behavior for large/medium app bars. */
    EXIT_UNTIL_COLLAPSED
}

/**
 * A data class that holds all the configuration for building a top bar.
 * This is the "blueprint" each screen provides.
 */
data class TopAppBarConfig(
    val title: @Composable () -> Unit,
    val navigationIcon: @Composable (() -> Unit)? = null,
    val actions: @Composable RowScope.() -> Unit = {},
    val scrollBehaviorType: ScrollBehaviorType = ScrollBehaviorType.NONE
)

/**
 * The state that the MainViewModel will hold. It's either hidden
 * or visible with a specific configuration.
 */
sealed class TopBarState {
    data object Hidden : TopBarState()
    data class Visible(val config: TopAppBarConfig) : TopBarState()
}

/**
 * The single, centralized composable that builds the TopAppBar UI.
 * This ensures all app bars in the app are consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAppTopAppBar(
    config: TopAppBarConfig,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = config.title,
        navigationIcon = { config.navigationIcon?.invoke() },
        actions = config.actions,
        scrollBehavior = scrollBehavior
    )
}