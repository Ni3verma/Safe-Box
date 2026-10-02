package com.andryoga.safebox.ui.core.appupdate

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Shows the "update downloaded" snackbar with a Restart action on the Home screen's snackbar host.
 *
 * It only ever shows while the vault is unlocked, which is exactly when Home is on screen, so it
 * reuses Home's host instead of adding a second one. Analytics are logged by the ViewModel on the
 * state transition, never here, because this effect runs again after every configuration change.
 *
 * @param viewModel The activity-scoped ViewModel shared with [InAppUpdateHostRoot]. It is passed
 * down from `AppNavigation` because inside a nav destination `hiltViewModel()` would scope a
 * second instance to the back stack entry.
 * @param snackbarHostState Home's global snackbar host.
 */
@Composable
fun InAppUpdateRestartPromptRoot(
    viewModel: InAppUpdateViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsState()

    InAppUpdateRestartPrompt(
        showRestartPrompt = uiState.showRestartPrompt,
        snackbarHostState = snackbarHostState,
        screenAction = viewModel::onAction,
    )
}

@Composable
private fun InAppUpdateRestartPrompt(
    showRestartPrompt: Boolean,
    snackbarHostState: SnackbarHostState,
    screenAction: (InAppUpdateAction) -> Unit,
) {
    val message = stringResource(R.string.in_app_update_downloaded)
    val actionLabel = stringResource(R.string.in_app_update_restart)

    LaunchedEffect(showRestartPrompt) {
        if (!showRestartPrompt) return@LaunchedEffect
        // Leaving composition, for example when the vault locks, cancels this call and removes
        // the snackbar without a result, so nothing is reported.
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        // showSnackbar suspends until the snackbar is gone. It returns ActionPerformed only when
        // the action button, which is Restart here, was tapped, and Dismissed for ✕ or dismiss().
        screenAction(
            if (result == SnackbarResult.ActionPerformed) {
                InAppUpdateAction.OnRestartClick
            } else {
                InAppUpdateAction.OnRestartPromptDismissed
            },
        )
    }
}

/**
 * The snackbar as Home's default `SnackbarHost` renders it. The real one is driven through
 * `showSnackbar`, which needs a running host and cannot be previewed, so the same visuals are fed
 * to Material's `Snackbar` directly.
 */
@LightDarkModePreview
@Composable
private fun InAppUpdateRestartSnackbarPreview() {
    val visuals = PreviewSnackbarVisuals(
        message = stringResource(R.string.in_app_update_downloaded),
        actionLabel = stringResource(R.string.in_app_update_restart),
    )
    SafeBoxTheme {
        Snackbar(snackbarData = PreviewSnackbarData(visuals))
    }
}

private class PreviewSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

private class PreviewSnackbarData(override val visuals: SnackbarVisuals) : SnackbarData {
    override fun performAction() = Unit
    override fun dismiss() = Unit
}
