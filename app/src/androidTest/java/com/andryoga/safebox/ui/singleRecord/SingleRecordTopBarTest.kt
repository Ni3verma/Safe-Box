package com.andryoga.safebox.ui.singleRecord

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarActions
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarNavIcon
import com.andryoga.safebox.ui.singleRecord.components.SingleRecordTopBarTitle
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite for SingleRecord Top App Bar components
 * (`SingleRecordTopBarTitle`, `SingleRecordTopBarNavIcon`, and `SingleRecordTopBarActions`).
 */
@RunWith(AndroidJUnit4::class)
class SingleRecordTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun topBarTitle_shouldRenderTitleString() {
        val testTitle = "Sample Record Title"

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordTopBarTitle(title = testTitle)
            }
        }

        composeTestRule.onNodeWithText(testTitle).assertIsDisplayed()
    }

    @Test
    fun topBarNavIcon_clickBackButtonShouldTriggerOnClickCallback() {
        var backClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordTopBarNavIcon(onClick = { backClicked = true })
            }
        }

        val backDesc = context.getString(R.string.cd_back_button)
        composeTestRule.onNodeWithContentDescription(backDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backDesc).performClick()

        assertThat(backClicked).isTrue()
    }

    @Test
    fun topBarActions_whenSaveVisibleAndEnabled_shouldShowPulseButtonAndEmitSave() {
        var saveClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordTopBarActions(
                    uiState = SingleRecordScreenUiState.TopAppBarUiState(
                        title = "Edit Mode",
                        isSaveButtonVisible = true,
                        isSaveButtonEnabled = true
                    ),
                    onSaveClick = { saveClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()

        assertThat(saveClicked).isTrue()
    }

    @Test
    fun topBarActions_whenSaveNotVisible_shouldNotRenderSaveButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordTopBarActions(
                    uiState = SingleRecordScreenUiState.TopAppBarUiState(
                        title = "View Mode",
                        isSaveButtonVisible = false
                    ),
                    onSaveClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertDoesNotExist()
    }

    @Test
    fun editModeWithValidFields_saveButtonShouldBeVisibleAndEnabled_andInvisibleWhenInvalid() {
        var isSaveVisible by androidx.compose.runtime.mutableStateOf(true)

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordTopBarActions(
                    uiState = SingleRecordScreenUiState.TopAppBarUiState(
                        title = "Edit Mode",
                        isSaveButtonVisible = isSaveVisible,
                        isSaveButtonEnabled = true
                    ),
                    onSaveClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()

        isSaveVisible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertDoesNotExist()

        isSaveVisible = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()
    }
}
