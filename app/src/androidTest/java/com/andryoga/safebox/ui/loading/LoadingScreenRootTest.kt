package com.andryoga.safebox.ui.loading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration UI Test suite for [LoadingScreenRoot] (Splash Screen & Boot Routing).
 * Verifies that state changes emitted by MainViewModel cleanly trigger LaunchedEffect navigation inside AppNavigation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoadingScreenRootTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun bootWhenSignupRequiredTrue_shouldNavigateToSignupRoute() {
        runBlocking {
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.onNodeWithText(context.getString(R.string.welcome))
                .assertIsDisplayed()
        }
    }

    @Test
    fun bootWhenSignupRequiredFalse_shouldNavigateToLoginRoute() {
        runBlocking {
            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                false
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()
        }
    }
}
