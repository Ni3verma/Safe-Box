@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [HomeViewModel]: the legacy-session upgrade banner with its *Retry now* lock, and
 * the screen-lock nudge with its three analytics events.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var deviceSecurityStatus: DeviceSecurityStatus

    @RelaxedMockK
    lateinit var activeSessionManager: ActiveSessionManager

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var viewModel: HomeViewModel

    private val preferenceProvider = FakePreferenceProvider()
    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x33 }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        sessionKeyHolder = SessionKeyHolderImpl()
        analyticsHelper = FakeAnalyticsHelper()
        givenDeviceSecure(false)
        viewModel = HomeViewModel(
            sessionKeyHolder = sessionKeyHolder,
            preferenceProvider = preferenceProvider,
            deviceSecurityStatus = deviceSecurityStatus,
            activeSessionManager = activeSessionManager,
            analyticsHelper = analyticsHelper,
        )
    }

    private fun givenDeviceSecure(isDeviceSecure: Boolean) {
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = isDeviceSecure,
            biometricClass = BiometricClass.NONE_ENROLLED,
            hasStrongBox = false,
        )
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect { } }
    }

    @Test
    fun uiState_whenSessionIsDekUnlocked_isLegacySessionIsFalse() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isLegacySession).isFalse()
        }
    }

    @Test
    fun uiState_whenLegacySessionOpenedAndRetryClicked_togglesIsLegacySessionAndClearsKey() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().isLegacySession).isTrue()

            viewModel.onRetryUpgradeClick()
            advanceUntilIdle()

            assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
            assertThat(expectMostRecentItem().isLegacySession).isFalse()
        }
    }

    @Test
    fun nudge_withoutALockScreen_shouldShowAfterTheFirstResume() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        collectUiState()
        advanceUntilIdle()
        // Unknown until read: never flashes before the device state is known.
        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isFalse()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isTrue()
    }

    @Test
    fun nudge_whenALockScreenIsSetAndHomeResumes_shouldDisappear() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        collectUiState()
        viewModel.onScreenResumed()
        advanceUntilIdle()

        givenDeviceSecure(true)
        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isFalse()
    }

    @Test
    fun nudge_afterTheRiskWasAccepted_shouldStayHidden() = runTest {
        preferenceProvider.upsertBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, true)
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        collectUiState()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isFalse()
    }

    @Test
    fun nudge_inALegacySession_shouldGiveWayToTheUpgradeBanner() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        collectUiState()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLegacySession).isTrue()
        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isFalse()
    }

    @Test
    fun nudgeShown_shouldLogTheShowEvent() {
        viewModel.onScreenLockNudgeShown()

        assertThat(analyticsHelper.count(AnalyticsKey.SCREEN_LOCK_NUDGE_SHOW)).isEqualTo(1)
    }

    @Test
    fun nudgeSetUpClick_whenSettingsOpened_shouldLogAndPauseTheAwayTimer() {
        viewModel.onScreenLockNudgeSetUpClick(isSettingsOpened = true)

        assertThat(analyticsHelper.count(AnalyticsKey.SCREEN_LOCK_NUDGE_OPEN_SETTINGS_CLICK)).isEqualTo(1)
        verify(exactly = 1) { activeSessionManager.setPaused(true) }
    }

    @Test
    fun nudgeSetUpClick_whenSettingsCouldNotOpen_shouldLogButNotPauseTheAwayTimer() {
        viewModel.onScreenLockNudgeSetUpClick(isSettingsOpened = false)

        assertThat(analyticsHelper.count(AnalyticsKey.SCREEN_LOCK_NUDGE_OPEN_SETTINGS_CLICK)).isEqualTo(1)
        verify(exactly = 0) { activeSessionManager.setPaused(any()) }
    }

    @Test
    fun nudgeAcceptRiskClick_shouldLogPersistTheDismissalAndHideTheBannerAtOnce() = runTest {
        sessionKeyHolder.setSessionKey(testDek.copyOf())
        collectUiState()
        viewModel.onScreenResumed()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isTrue()

        viewModel.onScreenLockNudgeAcceptRiskClick()
        advanceUntilIdle()

        assertThat(analyticsHelper.count(AnalyticsKey.SCREEN_LOCK_NUDGE_ACCEPT_RISK_CLICK)).isEqualTo(1)
        assertThat(preferenceProvider.getBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, false)).isTrue()
        assertThat(viewModel.uiState.value.isScreenLockNudgeVisible).isFalse()
    }
}
