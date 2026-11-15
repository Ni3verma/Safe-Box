package com.andryoga.safebox.ui.home

/**
 * UI state for Home-level banners; at most one is visible.
 *
 * @property isLegacySession True when the vault is open in a temporary legacy session (*Continue for now*).
 * @property isScreenLockNudgeVisible The device has no lock screen and the user has not accepted
 * the risk; without one a forgotten master password cannot be recovered.
 */
data class HomeUiState(
    // TODO(D3): legacy vault (crypto_version = 1) only
    val isLegacySession: Boolean = false,
    val isScreenLockNudgeVisible: Boolean = false,
)
