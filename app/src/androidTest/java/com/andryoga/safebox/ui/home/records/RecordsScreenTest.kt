package com.andryoga.safebox.ui.home.records

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.home.records.models.NotificationPermissionState
import com.andryoga.safebox.ui.home.records.models.UserInputs
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite for [RecordsScreen].
 */
@RunWith(AndroidJUnit4::class)
class RecordsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialStateZeroRecords_shouldShowNoRecordsTextAndButtons() {
        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        records = emptyList(),
                        totalDbRecords = 0
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.restore_records_button))
            .assertIsDisplayed()
    }

    @Test
    fun filteredOutRecordsState_shouldShowNoResultsFoundAndFilters() {
        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        records = emptyList(),
                        totalDbRecords = 5
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.no_filtered_record_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.no_filtered_record_body))
            .assertIsDisplayed()
    }

    @Test
    fun populatedListState_shouldRenderItemsAndEmitClick() {
        var clickedId: Int? = null
        var clickedType: RecordType? = null

        val sampleRecords = listOf(
            RecordListItem(
                id = 101,
                title = "Personal Gmail",
                subTitle = "user@gmail.com",
                recordType = RecordType.LOGIN
            ),
            RecordListItem(
                id = 102,
                title = "Visa Credit",
                subTitle = "**** 4321",
                recordType = RecordType.CARD
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        records = sampleRecords,
                        totalDbRecords = 2
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = { action ->
                        if (action is RecordScreenAction.OnRecordClick) {
                            clickedId = action.id
                            clickedType = action.recordType
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Personal Gmail").assertIsDisplayed()
        composeTestRule.onNodeWithText("user@gmail.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visa Credit").assertIsDisplayed()

        // Perform click and verify action
        composeTestRule.onNodeWithText("Personal Gmail").performClick()
        assertThat(clickedId).isEqualTo(101)
        assertThat(clickedType).isEqualTo(RecordType.LOGIN)
    }

    @Test
    fun clickFilterChip_shouldEmitToggleFilterAction() {
        var toggledRecordType: RecordType? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        records = emptyList(),
                        totalDbRecords = 5,
                        recordTypeFilters = listOf(
                            UserInputs.RecordTypeFilter(RecordType.LOGIN, false),
                            UserInputs.RecordTypeFilter(RecordType.CARD, false)
                        )
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = { action ->
                        if (action is RecordScreenAction.OnToggleRecordTypeFilter) {
                            toggledRecordType = action.recordType
                        }
                    }
                )
            }
        }

        val loginFilterLabel = context.getString(R.string.type_display_login)
        composeTestRule.onNode(hasText(loginFilterLabel)).performClick()
        assertThat(toggledRecordType).isEqualTo(RecordType.LOGIN)
    }

    @Test
    fun addNewRecordBottomSheet_shouldRenderRecordTypesAndEmitAction() {
        var selectedRecordType: RecordType? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        isShowAddNewRecordsBottomSheet = true,
                        records = emptyList(),
                        totalDbRecords = 5,
                        recordTypeFilters = emptyList()
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = { action ->
                        if (action is RecordScreenAction.OnAddNewRecord) {
                            selectedRecordType = action.recordType
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.add_a_new_record))
            .assertIsDisplayed()
        val loginTypeTitle = context.getString(R.string.type_display_login)
        composeTestRule.onNodeWithText(loginTypeTitle).assertIsDisplayed()

        composeTestRule.onNodeWithText(loginTypeTitle).performClick()
        assertThat(selectedRecordType).isEqualTo(RecordType.LOGIN)
    }

    @Test
    fun longPopulatedList_shouldBeScrollableToEndAndLastRecordFullyVisible() {
        val fiftyRecords = (1..50).map { index ->
            RecordListItem(
                id = index,
                title = if (index == 50) "LAST_RECORD_INDEX_50" else "Record $index",
                subTitle = "sub $index",
                recordType = RecordType.LOGIN
            )
        }

        composeTestRule.setContent {
            SafeBoxTheme {
                RecordsScreen(
                    uiState = RecordsUiState(
                        isLoading = false,
                        records = fiftyRecords,
                        totalDbRecords = 50
                    ),
                    notificationPermissionState = NotificationPermissionState(),
                    onRestoreFromBackup = {},
                    onScreenAction = {}
                )
            }
        }

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(50)
        composeTestRule.onNodeWithText("LAST_RECORD_INDEX_50").assertIsDisplayed()
    }
}
