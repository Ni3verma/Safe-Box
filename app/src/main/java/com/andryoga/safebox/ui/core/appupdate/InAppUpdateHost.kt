package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed

/**
 * App-level, invisible host for the in-app update flow. It must sit outside the root `NavHost`,
 * so that:
 *
 * - its [InAppUpdateViewModel] is activity-scoped and shared with [InAppUpdateRestartPromptRoot];
 * - the result launcher outlives screen changes, so the consent result still arrives if the vault
 *   locks while Play's sheet is open;
 * - the silent install for a locked vault also runs on the login screen.
 *
 * The state is collected with `collectAsState` rather than a lifecycle-aware collector on
 * purpose. If the away timeout locks the vault while the app is in the background, a downloaded
 * update is installed right then. Play installs silently when the app is not in the foreground.
 *
 * @param isInHomeGraph Whether the root navigation is inside the Home graph, or `null` before the
 * first destination is known.
 */
@Composable
fun InAppUpdateHostRoot(isInHomeGraph: Boolean?) {
    val viewModel = hiltViewModel<InAppUpdateViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    InAppUpdateHost(
        uiState = uiState,
        isInHomeGraph = isInHomeGraph,
        screenAction = viewModel::onAction,
    )
}

/**
 * Renders nothing. Registers the launcher that Play starts its consent sheet with, reports the
 * root destination, and asks for the sheet once [InAppUpdateUiState.promptVersionCode] is set.
 */
@Composable
private fun InAppUpdateHost(
    uiState: InAppUpdateUiState,
    isInHomeGraph: Boolean?,
    screenAction: (InAppUpdateAction) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        screenAction(
            InAppUpdateAction.OnUpdateFlowResult(UpdateFlowResult.fromResultCode(result.resultCode)),
        )
    }

    LaunchedEffect(isInHomeGraph) {
        if (isInHomeGraph != null) {
            screenAction(InAppUpdateAction.OnHomeGraphStateChanged(isInHomeGraph))
        }
    }

    val promptVersionCode = uiState.promptVersionCode
    LaunchedEffect(promptVersionCode) {
        if (promptVersionCode == null) return@LaunchedEffect
        // The OS can block activity launches from the background. A launch that Play accepts is
        // persisted as this version's only prompt, so wait until the user can actually see it.
        lifecycleOwner.withResumed {
            screenAction(InAppUpdateAction.OnLaunchUpdateFlow(promptVersionCode, launcher))
        }
    }
}
