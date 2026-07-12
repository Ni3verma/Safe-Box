@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-End (E2E) Full Stack UI Integration Tests for SafeBox Signup and Login flows.
 *
 * Exercises the entire application architecture:
 * - MainActivity & AppNavigation / NavHost routing
 * - Hilt Dependency Injection (@HiltAndroidTest)
 * - SharedPreferences / EncryptedPreferenceProvider persistence (IS_SIGN_UP_REQUIRED flag)
 * - Room Database (UserDetailsRepository / UserDetailsDaoSecure / SafeBoxDatabase)
 * - Multi-screen navigation (Signup -> Home (0 records) and Login -> Home)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SignupAndLoginE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun firstLaunch_signupFlow_shouldPersistCredentialsAndNavigateToHomeWithZeroRecords() =
        runTest {
            // Given: Fresh app state with zero records and signup required
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
            safeBoxDatabase.clearAllTables()

            ActivityScenario.launch(MainActivity::class.java).use { _ ->
                // 1. Verify Signup Screen is displayed on first boot
                composeTestRule.onNodeWithText(context.getString(R.string.welcome))
                    .assertIsDisplayed()

                // 2. Enter valid master password and hint (using performTextReplacement to override any debug pre-fills)
                composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
                    .performTextReplacement("Qwerty@@123")
                composeTestRule.onNode(
                    hasSetTextAction() and hasText(
                        context.getString(R.string.hint),
                        substring = true
                    )
                )
                    .performTextReplacement("E2E hint")

                // 3. Click Sign Up
                composeTestRule.onNodeWithText(context.getString(R.string.signup)).performClick()

                // 4. Verify clean transition to Home screen showing 0 records state
                composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                    .assertIsDisplayed()

                // 5. Verify database and preference persistence directly from injected singletons
                val isSignupStillRequired = encryptedPreferenceProvider.getBooleanPref(
                    CommonConstants.IS_SIGN_UP_REQUIRED,
                    true
                )
                assertThat(isSignupStillRequired).isFalse()

                val isPasswordCorrect = userDetailsRepository.checkPassword("Qwerty@@123")
                assertThat(isPasswordCorrect).isTrue()
            }
        }

    @Test
    fun subsequentLaunch_loginFlow_shouldRejectWrongPasswordAndNavigateToHomeOnCorrectPassword() =
        runTest {
            // Given: Pre-seeded credentials from a previous signup and IS_SIGN_UP_REQUIRED = false
            safeBoxDatabase.clearAllTables()
            userDetailsRepository.insertUserDetailsData("Qwerty@@123", "E2E hint")
            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                false
            )

            ActivityScenario.launch(MainActivity::class.java).use { _ ->
                // 1. Verify Login Screen is displayed on subsequent launch
                composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                    .assertIsDisplayed()

                // 2. Attempt login with wrong password and verify rejection
                composeTestRule.onNode(
                    hasSetTextAction() and hasText(
                        context.getString(R.string.password),
                        substring = true
                    )
                )
                    .performTextReplacement("WrongPass123")
                composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
                composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message))
                    .assertIsDisplayed()

                // 3. Enter correct master password and submit
                composeTestRule.onNode(
                    hasSetTextAction() and hasText(
                        context.getString(R.string.password),
                        substring = true
                    )
                )
                    .performTextReplacement("Qwerty@@123")
                composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()

                // 4. Verify successful authentication unlocks and opens Home screen showing 0 records
                composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                    .assertIsDisplayed()
            }
        }
}
