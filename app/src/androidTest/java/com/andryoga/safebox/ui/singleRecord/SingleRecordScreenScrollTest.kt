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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.R
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

    @Test
    fun enterTextInMiddleOfNote_shouldUpdateTextAndRemainVisible() {
        val initialNoteText = "this is my primary google account"
        val updatedNoteText = "this is my primary [EDITED MIDDLE] google account"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = true)
            )
        }

        composeTestRule.onNodeWithText(initialNoteText)
            .assertIsDisplayed()
            .performTextReplacement(updatedNoteText)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(updatedNoteText)
            .assertIsDisplayed()
    }

    @Test
    fun enterTextAtVeryEndOfNote_shouldAppendTextAndRemainVisible() {
        val initialNoteText = "this is my primary google account"
        val appendedBottomLineText = "\nNEW BOTTOM LINE TEXT"
        val expectedFullText = "$initialNoteText$appendedBottomLineText"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = true)
            )
        }

        composeTestRule.onNodeWithText(initialNoteText)
            .performTextReplacement(expectedFullText)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(expectedFullText)
            .assertIsDisplayed()
    }

    @Test
    fun enterEightContinuousNewlinesFollowedByText_shouldNotClipContent() {
        val eightNewlinesAndText = "\n\n\n\n\n\n\n\nTC3_8_LINES_VERIFIED"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = false)
            )
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))
            .performTextInput(eightNewlinesAndText)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(eightNewlinesAndText)
            .assertIsDisplayed()
    }

    @Test
    fun enterSixteenContinuousNewlines_shouldHonorMaxHeightAndScrollInternally() {
        val sixteenNewlinesAndText = "\n".repeat(16) + "TC4_16_LINES_VERIFIED"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getLoginLayoutPlan(withData = false)
            )
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))
            .performTextInput(sixteenNewlinesAndText)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(sixteenNewlinesAndText)
            .assertIsDisplayed()
    }

    @Test
    fun clickBottomNotesFieldOnUnscrolledForm_shouldAutoBringFieldIntoView() {
        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getBankAccountLayoutPlan(withData = true)
            )
        }

        composeTestRule.onNodeWithText("Notes")
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Notes")
            .assertIsDisplayed()
    }

    @Test
    fun clickAndTypeInSingleLineField_shouldUpdateAndRemainVisible() {
        val targetFieldName = "Customer Name"
        val inputCustomerName = "NITIN VERMA"

        composeTestRule.setContent {
            StatefulSingleRecordScreenTestHost(
                initialPlan = getBankAccountLayoutPlan(withData = false)
            )
        }

        composeTestRule.onNode(hasSetTextAction() and hasText(targetFieldName))
            .performClick()
            .performTextInput(inputCustomerName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(inputCustomerName)
            .assertIsDisplayed()
    }

    @Test
    fun viewMode_longNotes_shouldBeScrollableAndDisplayBottomText() {
        val longNotesBody =
            "Start of long notes\n" + "Line content inside notes\n".repeat(40) + "END_OF_LONG_NOTES_VIEW_MODE"
        val longNotesPlan = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan(
            id = com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId.LOGIN,
            arrangement = listOf(
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE
                    )
                ),
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_NOTES
                    )
                )
            ),
            fieldUiState = mapOf(
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.title,
                        isMandatory = true
                    ),
                    data = "Long Notes Title"
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_NOTES to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.notes,
                        minLines = 4,
                        maxLines = 10
                    ),
                    data = longNotesBody
                )
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = longNotesPlan
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("END_OF_LONG_NOTES_VIEW_MODE", substring = true)
            .performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("END_OF_LONG_NOTES_VIEW_MODE", substring = true)
            .assertIsDisplayed()
    }
}
