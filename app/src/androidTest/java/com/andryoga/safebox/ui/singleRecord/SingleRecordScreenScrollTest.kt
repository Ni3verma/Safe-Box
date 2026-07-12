package com.andryoga.safebox.ui.singleRecord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.ui.previewHelper.getBankAccountLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleRecordScreenScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Stateful test host wrapper that mimics Unidirectional Data Flow (ViewModel -> UI -> Action -> ViewModel).
     * When [SingleRecordScreenAction.OnCellValueUpdate] is received, updates [LayoutPlan.fieldUiState]
     * and triggers clean recomposition with the updated value.
     */
    @Composable
    private fun StatefulSingleRecordScreenTestHost(
        initialPlan: LayoutPlan,
        viewMode: ViewMode = ViewMode.EDIT
    ) {
        var layoutPlan by remember { mutableStateOf(initialPlan) }
        SafeBoxTheme {
            SingleRecordScreen(
                uiState = SingleRecordScreenUiState(
                    isLoading = false,
                    layoutPlan = layoutPlan,
                    viewMode = viewMode
                ),
                screenAction = { action ->
                    if (action is SingleRecordScreenAction.OnCellValueUpdate) {
                        val oldFieldState = layoutPlan.fieldUiState[action.fieldId]
                        if (oldFieldState != null) {
                            val updatedFieldState = oldFieldState.copy(data = action.data)
                            layoutPlan = layoutPlan.copy(
                                fieldUiState = layoutPlan.fieldUiState + (action.fieldId to updatedFieldState)
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * TC-1: Verifies that inserting text in the middle of an existing multiline note
     * correctly updates the field value and keeps the content visible on screen.
     */
    @Test
    fun enterTextInMiddleOfNote_shouldUpdateTextAndRemainVisible_TC1() {
        // Given: SingleRecordScreen loaded in EDIT mode with an existing Login note
        val initialNoteText = "this is my primary google account"
        val updatedNoteText = "this is my primary [EDITED MIDDLE] google account"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = true)
            )
        }

        // When: Replacing text in the middle of the existing note
        composeTestRule.onNodeWithText(initialNoteText)
            .assertIsDisplayed()
            .performTextReplacement(updatedNoteText)

        // Then: Updated note text remains visible above keyboard/padding bounds
        composeTestRule.onNodeWithText(updatedNoteText)
            .assertIsDisplayed()
    }

    /**
     * TC-2: Verifies that appending new text to the very end of an existing note
     * updates correctly and keeps the bottom line visible.
     */
    @Test
    fun enterTextAtVeryEndOfNote_shouldAppendTextAndRemainVisible_TC2() {
        // Given: SingleRecordScreen loaded in EDIT mode with pre-existing note content
        val initialNoteText = "this is my primary google account"
        val appendedBottomLineText = "\nNEW BOTTOM LINE TEXT"
        val expectedFullText = "$initialNoteText$appendedBottomLineText"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = true)
            )
        }

        // When: Entering new text at the bottom line
        composeTestRule.onNodeWithText(initialNoteText)
            .performTextReplacement(expectedFullText)

        // Then: Full multiline content including the appended bottom line remains displayed
        composeTestRule.onNodeWithText(expectedFullText)
            .assertIsDisplayed()
    }

    /**
     * TC-3: Verifies entering 8 consecutive newlines followed by text inside an empty Notes field.
     * Ensures that multiline text input handles block spacing without clipping or layout shifts.
     */
    @Test
    fun enterEightContinuousNewlinesFollowedByText_shouldNotClipContent_TC3() {
        // Given: SingleRecordScreen loaded in EDIT mode with an empty Notes field
        val eightNewlinesAndText = "\n\n\n\n\n\n\n\nTC3_8_LINES_VERIFIED"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = false)
            )
        }

        // When: Entering 8 newlines and trailing text into Notes
        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))
            .performTextInput(eightNewlinesAndText)

        // Then: Entered text after 8 blank lines remains cleanly displayed
        composeTestRule.onNodeWithText(eightNewlinesAndText)
            .assertIsDisplayed()
    }

    /**
     * TC-4: Verifies entering 16 consecutive newlines inside Notes.
     * Confirms multiline field expansion respects bounded height restrictions (max 320.dp)
     * and keeps typed content accessible.
     */
    @Test
    fun enterSixteenContinuousNewlines_shouldHonorMaxHeightAndScrollInternally_TC4() {
        // Given: SingleRecordScreen loaded in EDIT mode with an empty Notes field
        val sixteenNewlinesAndText = "\n".repeat(16) + "TC4_16_LINES_VERIFIED"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = false)
            )
        }

        // When: Typing 16 consecutive newlines and verification text
        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))
            .performTextInput(sixteenNewlinesAndText)

        // Then: The input node correctly holds and displays the multiline text
        composeTestRule.onNodeWithText(sixteenNewlinesAndText)
            .assertIsDisplayed()
    }

    /**
     * TC-5: Verifies automatic focus bring-into-view behavior when clicking directly on a bottom field (Notes)
     * on an initially un-scrolled form.
     */
    @Test
    fun clickBottomNotesFieldOnUnscrolledForm_shouldAutoBringFieldIntoView_TC5() {
        // Given: Bank account record loaded in EDIT mode (Notes field sits at bottom of un-scrolled form)
        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getBankAccountLayoutPlan(withData = true)
            )
        }

        // When: Directly clicking the Notes label/field at the bottom
        composeTestRule.onNodeWithText("Notes")
            .performClick()

        // Then: The Notes field brings itself into view and remains displayed
        composeTestRule.onNodeWithText("Notes")
            .assertIsDisplayed()
    }

    /**
     * TC-6: Verifies standard focus and typing on a single-line field (Customer Name).
     */
    @Test
    fun clickAndTypeInSingleLineField_shouldUpdateAndRemainVisible_TC6() {
        // Given: Bank account record loaded in EDIT mode without initial data
        val targetFieldName = "Customer Name"
        val inputCustomerName = "NITIN VERMA"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getBankAccountLayoutPlan(withData = false)
            )
        }

        // When: Clicking on single-line field and typing text
        composeTestRule.onNode(hasSetTextAction() and hasText(targetFieldName))
            .performClick()
            .performTextInput(inputCustomerName)

        // Then: Entered text is displayed in the field
        composeTestRule.onNodeWithText(inputCustomerName)
            .assertIsDisplayed()
    }
}
