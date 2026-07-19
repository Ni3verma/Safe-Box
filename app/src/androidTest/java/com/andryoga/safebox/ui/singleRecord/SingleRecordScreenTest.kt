package com.andryoga.safebox.ui.singleRecord

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite for [SingleRecordScreen].
 */
@RunWith(AndroidJUnit4::class)
class SingleRecordScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun viewMode_shouldShowActionButtons() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = {}
                )
            }
        }

        val editDesc = context.getString(R.string.cd_action_edit)
        val shareDesc = context.getString(R.string.cd_action_share)
        val deleteDesc = context.getString(R.string.cd_action_delete)

        composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(shareDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(deleteDesc).assertIsDisplayed()
    }

    @Test
    fun clickEditButton_shouldEmitOnEditClickedAction() {
        var editClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnEditClicked) {
                            editClicked = true
                        }
                    }
                )
            }
        }

        val editDesc = context.getString(R.string.cd_action_edit)
        composeTestRule.onNodeWithContentDescription(editDesc).performClick()
        composeTestRule.waitForIdle()
        assertThat(editClicked).isTrue()
    }

    @Test
    fun clickShareButton_shouldEmitOnShareClickedAction() {
        var shareClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnShareClicked) {
                            shareClicked = true
                        }
                    }
                )
            }
        }

        val shareDesc = context.getString(R.string.cd_action_share)
        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        composeTestRule.waitForIdle()
        assertThat(shareClicked).isTrue()
    }

    @Test
    fun clickDeleteButtonAndConfirm_shouldShowDialogAndEmitOnDeleteClickedAction() {
        var deleteClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnDeleteClicked) {
                            deleteClicked = true
                        }
                    }
                )
            }
        }

        val deleteDesc = context.getString(R.string.cd_action_delete)
        composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify alert dialog appears
        composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.delete_record_dialog_body))
            .assertIsDisplayed()

        // Click Confirm
        composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
        composeTestRule.waitForIdle()
        assertThat(deleteClicked).isTrue()
    }

    @Test
    fun clickDeleteButtonAndCancel_shouldDismissDialogWithoutEmittingAction() {
        var deleteClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnDeleteClicked) {
                            deleteClicked = true
                        }
                    }
                )
            }
        }

        val deleteDesc = context.getString(R.string.cd_action_delete)
        composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
            .assertIsDisplayed()

        // Click Cancel
        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()
        assertThat(deleteClicked).isFalse()
        composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
            .assertDoesNotExist()
    }

    @Test
    fun editMode_shouldHideActionButtonsAndAllowEditingFields() {
        var updatedData: String? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.EDIT,
                        layoutPlan = getLoginLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnCellValueUpdate) {
                            updatedData = action.data
                        }
                    }
                )
            }
        }

        // Verify action row buttons do not exist in EDIT mode
        val editDesc = context.getString(R.string.cd_action_edit)
        composeTestRule.onNodeWithContentDescription(editDesc).assertDoesNotExist()

        // Perform edit in Title field
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.title),
                substring = true
            )
        )
            .performTextInput(" Edited")
        composeTestRule.waitForIdle()

        assertThat(updatedData).isNotNull()
    }

    @Test
    fun viewMode_allPopulatedFields_shouldRenderLabelsAndFormattedData() {
        val customLayoutPlan = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan(
            id = com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId.LOGIN,
            arrangement = listOf(
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE
                    )
                ),
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_USER_ID
                    )
                ),
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.CARD_NUMBER
                    )
                )
            ),
            fieldUiState = mapOf(
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.title,
                        isMandatory = true
                    ),
                    data = "Test Title"
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_USER_ID to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.user_id,
                        isCopyable = true
                    ),
                    data = "test@user.com"
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.CARD_NUMBER to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.number,
                        visualTransformation = com.andryoga.safebox.ui.singleRecord.dynamicLayout.visualTransformers.SpaceAfterEveryFourCharsTransformation()
                    ),
                    data = "4111222233334444"
                )
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = customLayoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.user_id)).assertIsDisplayed()
        composeTestRule.onNodeWithText("test@user.com").assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.number)).assertIsDisplayed()
        composeTestRule.onNodeWithText("4111 2222 3333 4444").assertIsDisplayed()
    }

    @Test
    fun viewMode_unpopulatedFields_shouldNotRenderEmptyLabelsOrPlaceholders() {
        val customLayoutPlan = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan(
            id = com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId.LOGIN,
            arrangement = listOf(
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE
                    )
                ),
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_URL
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
                    data = "Only Title Entered"
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_URL to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.url
                    ),
                    data = ""
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_NOTES to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.notes
                    ),
                    data = ""
                )
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = customLayoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Only Title Entered").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.url)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.notes)).assertDoesNotExist()
    }

    @Test
    fun creationAndUpdatedDates_shouldBeVisibleOnlyInViewModeAndHiddenInEditMode() {
        var viewModeState by mutableStateOf(ViewMode.VIEW)
        val dateLayoutPlan = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan(
            id = com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId.LOGIN,
            arrangement = listOf(
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE
                    )
                ),
                listOf(
                    com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan.Field(
                        com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.CREATION_DATE
                    )
                )
            ),
            fieldUiState = mapOf(
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.LOGIN_TITLE to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.title,
                        isMandatory = true
                    ),
                    data = "Sample Title"
                ),
                com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId.CREATION_DATE to com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState(
                    cell = com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState.Cell(
                        label = R.string.created_on,
                        isVisibleOnlyInViewMode = true
                    ),
                    data = "12 Jul 2026, 10:00 AM"
                )
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = viewModeState,
                        layoutPlan = dateLayoutPlan
                    ),
                    screenAction = {}
                )
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.created_on)).assertIsDisplayed()
        composeTestRule.onNodeWithText("12 Jul 2026, 10:00 AM").assertIsDisplayed()

        viewModeState = ViewMode.EDIT
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.created_on)).assertDoesNotExist()
        composeTestRule.onNodeWithText("12 Jul 2026, 10:00 AM").assertDoesNotExist()
    }

    @Test
    fun passwordField_inEditModeShouldShowToggleIconAndMaskUnmaskText_andInvisibleInViewMode() {
        var viewModeState by mutableStateOf(ViewMode.EDIT)
        val passwordLayoutPlan =
            LayoutPlan(
                id = LayoutId.LOGIN,
                arrangement = listOf(
                    listOf(LayoutPlan.Field(FieldId.LOGIN_PASSWORD))
                ),
                fieldUiState = mapOf(
                    FieldId.LOGIN_PASSWORD to FieldUiState(
                        cell = FieldUiState.Cell(
                            label = R.string.password,
                            isPasswordField = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        ),
                        data = "SecretPass123"
                    )
                )
            )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = viewModeState,
                        layoutPlan = passwordLayoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        val toggleDesc = context.getString(R.string.cd_toggle_sensitive_data_visibility)
        val maskedText = "\u2022".repeat("SecretPass123".length)
        composeTestRule.onNodeWithContentDescription(toggleDesc).assertIsDisplayed()
        composeTestRule.onNode(hasText(maskedText) and hasSetTextAction())
            .assertIsDisplayed() // masked by default
        composeTestRule.onNodeWithContentDescription(toggleDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(toggleDesc).assertIsDisplayed()
        composeTestRule.onNode(hasText(maskedText) and hasSetTextAction())
            .assertDoesNotExist() // unmasked after toggle
        composeTestRule.onNode(hasText("SecretPass123") and hasSetTextAction())
            .assertIsDisplayed()

        viewModeState = ViewMode.VIEW
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(toggleDesc).assertDoesNotExist()
    }

    @Test
    fun copyableField_clickingInViewModeShouldCopyToClipboard() {
        var clipboardEntry: androidx.compose.ui.platform.ClipEntry? = null

        val copyLayoutPlan = LayoutPlan(
            id = LayoutId.LOGIN,
            arrangement = listOf(
                listOf(LayoutPlan.Field(FieldId.LOGIN_USER_ID))
            ),
            fieldUiState = mapOf(
                FieldId.LOGIN_USER_ID to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.user_id,
                        isCopyable = true
                    ),
                    data = "copyable@user.com"
                )
            )
        )

        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalClipboard provides object :
                    androidx.compose.ui.platform.Clipboard {
                    override suspend fun getClipEntry(): androidx.compose.ui.platform.ClipEntry? =
                        clipboardEntry

                    override suspend fun setClipEntry(clipEntry: androidx.compose.ui.platform.ClipEntry?) {
                        clipboardEntry = clipEntry
                    }

                    override val nativeClipboard: android.content.ClipboardManager
                        get() = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                }
            ) {
                SafeBoxTheme {
                    SingleRecordScreen(
                        uiState = SingleRecordScreenUiState(
                            isLoading = false,
                            viewMode = ViewMode.VIEW,
                            layoutPlan = copyLayoutPlan
                        ),
                        screenAction = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("copyable@user.com").performClick()
        composeTestRule.waitForIdle()

        assertThat(clipboardEntry).isNotNull()
        assertThat(clipboardEntry?.clipData?.getItemAt(0)?.text?.toString()).isEqualTo("copyable@user.com")
    }
}
