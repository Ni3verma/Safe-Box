package com.andryoga.safebox.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.home.components.ScreenLockNudgeBanner
import com.andryoga.safebox.ui.home.components.UpgradePendingBanner
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component tests for the Home banners: the screen-lock nudge and the legacy-session
 * upgrade-pending banner. Which banner shows is decided by `HomeViewModel` (unit-tested there).
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenBannersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun screenLockNudge_shouldExplainTheRiskAndReportItWasShownOnce() {
        var shownCount = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                ScreenLockNudgeBanner(onSetUpClick = {}, onAcceptRiskClick = {}, onShow = { shownCount++ })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.home_screen_lock_nudge_banner))
            .assertIsDisplayed()
        composeTestRule.waitForIdle()
        assertThat(shownCount).isEqualTo(1)
    }

    @Test
    fun screenLockNudge_setUpClick_shouldReachTheCallback() {
        var setUpClicks = 0
        var acceptRiskClicks = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                ScreenLockNudgeBanner(
                    onSetUpClick = { setUpClicks++ },
                    onAcceptRiskClick = { acceptRiskClicks++ },
                    onShow = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.home_screen_lock_nudge_set_up)).performClick()
        composeTestRule.waitForIdle()

        assertThat(setUpClicks).isEqualTo(1)
        assertThat(acceptRiskClicks).isEqualTo(0)
    }

    @Test
    fun screenLockNudge_acceptRiskClick_shouldReachTheCallback() {
        var setUpClicks = 0
        var acceptRiskClicks = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                ScreenLockNudgeBanner(
                    onSetUpClick = { setUpClicks++ },
                    onAcceptRiskClick = { acceptRiskClicks++ },
                    onShow = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.home_screen_lock_nudge_accept_risk))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(acceptRiskClicks).isEqualTo(1)
        assertThat(setUpClicks).isEqualTo(0)
    }

    @Test
    fun upgradePendingBanner_retryClick_shouldReachTheCallback() {
        var retryClicks = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                UpgradePendingBanner(onRetryClick = { retryClicks++ })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.home_upgrade_pending_banner))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.home_upgrade_pending_retry)).performClick()
        composeTestRule.waitForIdle()

        assertThat(retryClicks).isEqualTo(1)
    }
}
