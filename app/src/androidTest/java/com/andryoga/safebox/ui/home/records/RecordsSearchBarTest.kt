package com.andryoga.safebox.ui.home.records

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.home.records.components.RecordsSearchBarActions
import com.andryoga.safebox.ui.home.records.components.RecordsSearchBarTitle
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite for [RecordsSearchBarTitle] and [RecordsSearchBarActions].
 */
@RunWith(AndroidJUnit4::class)
class RecordsSearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun searchBarTitle_typingTextShouldWriteIntoTextFieldState() {
        val searchTextState = TextFieldState()

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsSearchBarTitle(searchTextState = searchTextState)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_bar_placeholder))
            .assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
            .performTextReplacement("abc")
        composeTestRule.waitForIdle()

        assertThat(searchTextState.text.toString()).isEqualTo("abc")
    }

    @Test
    fun searchBarTitle_appendingTextShouldKeepCaretAtEnd() {
        val searchTextState = TextFieldState()

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsSearchBarTitle(searchTextState = searchTextState)
            }
        }

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInput("abc")
        field.performTextInput("def")
        composeTestRule.waitForIdle()

        assertThat(searchTextState.text.toString()).isEqualTo("abcdef")
        assertThat(searchTextState.selection).isEqualTo(TextRange(6))
    }

    @Test
    fun searchBarActions_whenQueryNotEmpty_shouldShowClearButtonAndEmitClearAction() {
        var emittedAction: RecordScreenAction? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                Row {
                    RecordsSearchBarActions(
                        searchTextState = TextFieldState("Sample query"),
                        onScreenAction = { action -> emittedAction = action },
                    )
                }
            }
        }

        val clearDesc = context.getString(R.string.cd_clear_search_bar)
        composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true).onParent()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isEqualTo(RecordScreenAction.OnClearSearchText)
    }

    @Test
    fun searchBarActions_whenQueryEmpty_shouldNotShowClearButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                Row {
                    RecordsSearchBarActions(
                        searchTextState = TextFieldState(),
                        onScreenAction = {},
                    )
                }
            }
        }

        val clearDesc = context.getString(R.string.cd_clear_search_bar)
        composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun searchBarActions_clickAddNewButton_shouldEmitShowBottomSheet() {
        var showBottomSheetEmitted = false

        composeTestRule.setContent {
            SafeBoxTheme {
                Row {
                    RecordsSearchBarActions(
                        searchTextState = TextFieldState(),
                        onScreenAction = { action ->
                            if (action is RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet) {
                                showBottomSheetEmitted = action.showAddNewRecordBottomSheet
                            }
                        },
                    )
                }
            }
        }

        val addNewDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.onNodeWithContentDescription(addNewDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addNewDesc).performClick()
        composeTestRule.waitForIdle()

        assertThat(showBottomSheetEmitted).isTrue()
    }
}
