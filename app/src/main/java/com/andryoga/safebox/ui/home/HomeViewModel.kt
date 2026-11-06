package com.andryoga.safebox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.ui.core.ActiveSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Home-level banners: upgrade-pending in a legacy session, the screen-lock nudge otherwise.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder,
    private val preferenceProvider: PreferenceProvider,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val activeSessionManager: ActiveSessionManager,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    // Driving state, read on every resume (the user sets the lock screen elsewhere). `null` until
    // the first read, so the nudge never flashes before both are known.
    private val isDeviceSecure = MutableStateFlow<Boolean?>(null)
    private val isNudgeDismissed = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        sessionKeyHolder.isUnlocked,
        isDeviceSecure,
        isNudgeDismissed,
    ) { isUnlocked, isDeviceSecure, isNudgeDismissed ->
        val isLegacySession = isUnlocked && sessionKeyHolder.isLegacySession
        HomeUiState(
            isLegacySession = isLegacySession,
            // One banner at a time: a legacy session shows the upgrade banner instead.
            isScreenLockNudgeVisible = isUnlocked && !isLegacySession &&
                isDeviceSecure == false && isNudgeDismissed == false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            isLegacySession = sessionKeyHolder.isUnlocked.value && sessionKeyHolder.isLegacySession,
        ),
    )

    /** Home resumed: the lock screen may have been set or removed meanwhile. */
    fun onScreenResumed() {
        isDeviceSecure.value = deviceSecurityStatus.snapshot().isDeviceSecure
        viewModelScope.launch {
            isNudgeDismissed.value =
                preferenceProvider.getBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, false)
        }
    }

    /**
     * Locks the legacy session so the HomeGraph gate navigates back to the Login screen where the
     * user can retry the envelope migration.
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    fun onRetryUpgradeClick() {
        sessionKeyHolder.clearSessionKey()
    }

    fun onScreenLockNudgeShown() {
        analyticsHelper.logEvent(AnalyticsKey.SCREEN_LOCK_NUDGE_SHOW)
    }

    /**
     * *Set up* tapped. The away timer is paused while the system settings are in front; it resumes
     * with the app's next `onStart`.
     *
     * @param isSettingsOpened Whether the security settings activity was launched.
     */
    fun onScreenLockNudgeSetUpClick(isSettingsOpened: Boolean) {
        analyticsHelper.logEvent(AnalyticsKey.SCREEN_LOCK_NUDGE_OPEN_SETTINGS_CLICK)
        if (isSettingsOpened) activeSessionManager.setPaused(true)
    }

    fun onScreenLockNudgeAcceptRiskClick() {
        analyticsHelper.logEvent(AnalyticsKey.SCREEN_LOCK_NUDGE_ACCEPT_RISK_CLICK)
        isNudgeDismissed.value = true
        viewModelScope.launch {
            preferenceProvider.upsertBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, true)
        }
    }
}
