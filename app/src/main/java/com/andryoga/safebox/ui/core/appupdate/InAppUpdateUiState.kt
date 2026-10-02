package com.andryoga.safebox.ui.core.appupdate

/**
 * UI state published by [InAppUpdateViewModel].
 *
 * @property promptVersionCode When non-null, the host should launch Play's consent sheet for this
 * version. It returns to `null` as soon as the launch is attempted.
 * @property showRestartPrompt Whether the "update downloaded, restart" snackbar should be shown.
 * It is only true while the vault is unlocked.
 */
data class InAppUpdateUiState(
    val promptVersionCode: Int? = null,
    val showRestartPrompt: Boolean = false,
)
