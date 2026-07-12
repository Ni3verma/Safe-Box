@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider

/**
 * Common, non-duplicated utility methods for SafeBox End-to-End (E2E) Hilt integration tests.
 * Manages database setup, authentication unlock flows, and bottom sheet navigation triggers.
 */
object E2ETestUtils {

    const val TEST_MASTER_PASSWORD = "Qwerty@@123"
    const val TEST_MASTER_HINT = "E2E Master Hint"

    /**
     * Pre-seeds the database and encrypted preferences so the app boots directly onto the LoginScreen.
     */
    suspend fun setupUnlockedHomeState(
        safeBoxDatabase: SafeBoxDatabase,
        userDetailsRepository: UserDetailsRepository,
        encryptedPreferenceProvider: EncryptedPreferenceProvider,
        preferenceProvider: com.andryoga.safebox.providers.interfaces.PreferenceProvider
    ) {
        safeBoxDatabase.clearAllTables()
        userDetailsRepository.insertUserDetailsData(TEST_MASTER_PASSWORD, TEST_MASTER_HINT)
        encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, false)
        preferenceProvider.upsertIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, 0)
    }

    /**
     * Unlocks the app from [LoginScreen] and confirms transition to the Home [RecordsScreen].
     */
    fun unlockApp(composeTestRule: ComposeTestRule, context: Context) {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 15000L) {
            runCatching {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)))
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(context.getString(R.string.welcome_back)).assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextReplacement(TEST_MASTER_PASSWORD)
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Opens the [AddNewRecordBottomSheet] via the top app bar `+` button and clicks the specified record option.
     */
    fun clickAddNewRecordOption(
        composeTestRule: ComposeTestRule,
        context: Context,
        optionResId: Int
    ) {
        val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            runCatching {
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription(
                        addNewButtonDesc
                    )
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithContentDescription(addNewButtonDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addNewButtonDesc).performClick()

        val optionText = context.getString(optionResId)
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            runCatching {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasText(optionText))
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(optionText).assertIsDisplayed()
        composeTestRule.onNodeWithText(optionText).performClick()
    }
}
