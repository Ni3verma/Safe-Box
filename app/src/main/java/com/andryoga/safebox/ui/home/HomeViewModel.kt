package com.andryoga.safebox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state for Home-level banners.
 *
 * @property isLegacySession True when the vault is open in a temporary legacy session (*Continue for now*).
 */
// TODO(D3): legacy vault (crypto_version = 1) only — MR 7 adds the v2 screen-lock nudge state here
data class HomeUiState(
    val isLegacySession: Boolean = false,
)

/**
 * Owns the Home-level upgrade-pending banner.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = sessionKeyHolder.isUnlocked.map { isUnlocked ->
        HomeUiState(
            isLegacySession = isUnlocked && sessionKeyHolder.isLegacySession,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            isLegacySession = sessionKeyHolder.isUnlocked.value && sessionKeyHolder.isLegacySession,
        ),
    )

    /**
     * Locks the legacy session so the HomeGraph gate navigates back to the Login screen where the
     * user can retry the envelope migration.
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    fun onRetryUpgradeClick() {
        sessionKeyHolder.clearSessionKey()
    }
}
