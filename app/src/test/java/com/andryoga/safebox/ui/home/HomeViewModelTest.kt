@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [HomeViewModel] verifying the legacy-session upgrade banner state and *Retry now*
 * session lock.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        sessionKeyHolder = SessionKeyHolderImpl()
        viewModel = HomeViewModel(sessionKeyHolder = sessionKeyHolder)
    }

    @Test
    fun uiState_whenSessionIsDekUnlocked_isLegacySessionIsFalse() = runTest {
        val dek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x33 }
        sessionKeyHolder.setSessionKey(dek)

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
}
