package com.andryoga.safebox.ui.home.records

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
    fun searchBarTitle_typingTextShouldEmitOnSearchTextUpdate() {
        var emittedQuery: String? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsSearchBarTitle(
                    query = "",
                    onScreenAction = { action ->
                        if (action is RecordScreenAction.OnSearchTextUpdate) {
                            emittedQuery = action.searchText
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_bar_placeholder))
            .assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
            .performTextInput("abc")

        assertThat(emittedQuery).isEqualTo("abc")
    }

    @Test
    fun searchBarActions_whenQueryNotEmpty_shouldShowClearButtonAndEmitClear() {
        var emittedQuery: String? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                androidx.compose.foundation.layout.Row {
                    RecordsSearchBarActions(
                        query = "Sample query",
                        onScreenAction = { action ->
                            if (action is RecordScreenAction.OnSearchTextUpdate) {
                                emittedQuery = action.searchText
                            }
                        }
                    )
                }
            }
        }

        val clearDesc = context.getString(R.string.cd_clear_search_bar)
        composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true).onParent()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedQuery).isEqualTo("")
    }

    @Test
    fun searchBarActions_clickAddNewButton_shouldEmitShowBottomSheet() {
        var showBottomSheetEmitted = false

        composeTestRule.setContent {
            SafeBoxTheme {
                androidx.compose.foundation.layout.Row {
                    RecordsSearchBarActions(
                        query = "",
                        onScreenAction = { action ->
                            if (action is RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet) {
                                showBottomSheetEmitted = action.showAddNewRecordBottomSheet
                            }
                        }
                    )
                }
            }
        }

        val addNewDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.onNodeWithContentDescription(addNewDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addNewDesc).performClick()

        assertThat(showBottomSheetEmitted).isTrue()
    }
}
