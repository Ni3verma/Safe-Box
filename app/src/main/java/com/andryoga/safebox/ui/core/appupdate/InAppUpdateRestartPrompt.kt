package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.andryoga.safebox.R

/**
 * Shows the "update downloaded" snackbar with a Restart action on the Home screen's snackbar host.
 *
 * It only ever shows while the vault is unlocked, which is exactly when Home is on screen, so it
 * reuses Home's host instead of adding a second one. Analytics are logged by the ViewModel on the
 * state transition, never here, because this effect runs again after every configuration change.
 *
 * The snackbar is [SnackbarDuration.Indefinite], and `showSnackbar` queues behind it. Any future
 * snackbar caller should dismiss the current snackbar first, as `ClipboardActions` does. That
 * programmatic dismiss is reported as [InAppUpdateAction.OnRestartPromptDismissed], and the update
 * still installs at the next lock.
 *
 * @param snackbarHostState Home's global snackbar host.
 * @param viewModel The activity-scoped instance shared with [InAppUpdateHost].
 */
@Composable
fun InAppUpdateRestartPrompt(
    snackbarHostState: SnackbarHostState,
    viewModel: InAppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activityViewModelStoreOwner()),
) {
    val uiState by viewModel.uiState.collectAsState()
    val message = stringResource(R.string.in_app_update_downloaded)
    val actionLabel = stringResource(R.string.in_app_update_restart)

    val showRestartPrompt = uiState.showRestartPrompt
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
        viewModel.onAction(
            if (result == SnackbarResult.ActionPerformed) {
                InAppUpdateAction.OnRestartClick
            } else {
                InAppUpdateAction.OnRestartPromptDismissed
            },
        )
    }
}

/**
 * The hosting activity as the [ViewModelStoreOwner]. Inside a nav destination the default owner is
 * the back stack entry, which would create a second [InAppUpdateViewModel] instead of sharing the
 * one owned by [InAppUpdateHost].
 */
@Composable
private fun activityViewModelStoreOwner(): ViewModelStoreOwner =
    checkNotNull(LocalActivity.current as? ComponentActivity) {
        "InAppUpdateRestartPrompt must be hosted in a ComponentActivity"
    }
