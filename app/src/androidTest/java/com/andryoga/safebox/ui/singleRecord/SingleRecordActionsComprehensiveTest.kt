package com.andryoga.safebox.ui.singleRecord

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.getBankAccountLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getCardLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getLoginLayoutPlan
import com.andryoga.safebox.ui.previewHelper.getNoteLayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.visualTransformers.ExpiryDateTransformation
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.visualTransformers.SpaceAfterEveryFourCharsTransformation
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive component and integration UI test suite verifying viewing, editing, clipboard copying, masking, deletion, and sharing across all four record types.
 */
@RunWith(AndroidJUnit4::class)
class SingleRecordActionsComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // --- SENSITIVE DATA MASKING & TOGGLING (LOGIN & BANK CARD) ---

    @Test
    fun clickTogglePasswordIcon_shouldToggleVisualTransformationAndMaskUnmaskLoginPassword() {
        val basePlan = getLoginLayoutPlan(withData = true)
        val layoutPlan = basePlan.copy(
            fieldUiState = basePlan.fieldUiState + (
                    FieldId.LOGIN_PASSWORD to (basePlan.fieldUiState[FieldId.LOGIN_PASSWORD]?.copy(
                        cell = basePlan.fieldUiState[FieldId.LOGIN_PASSWORD]?.cell?.copy(
                            isPasswordField = true,
                            visualTransformation = PasswordVisualTransformation()
                        ) ?: FieldUiState.Cell(
                            label = R.string.password,
                            isPasswordField = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    ) ?: FieldUiState(
                        cell = FieldUiState.Cell(
                            label = R.string.password,
                            isPasswordField = true,
                            visualTransformation = PasswordVisualTransformation()
                        ),
                        data = "SecretPass123"
                    ))
                    )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.EDIT,
                        layoutPlan = layoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        val toggleDesc = context.getString(R.string.cd_toggle_sensitive_data_visibility)
        val rawPassword = layoutPlan.fieldUiState[FieldId.LOGIN_PASSWORD]?.data ?: ""
        val maskedPassword = "\u2022".repeat(rawPassword.length)

        // Initially masked
        composeTestRule.onNode(hasText(maskedPassword) and hasSetTextAction()).assertIsDisplayed()

        // Click toggle eye icon to unmask
        composeTestRule.onNodeWithContentDescription(toggleDesc).performClick()
        composeTestRule.onNode(hasText(rawPassword) and hasSetTextAction()).assertIsDisplayed()

        // Click toggle eye icon to mask again
        composeTestRule.onNodeWithContentDescription(toggleDesc).performClick()
        composeTestRule.onNode(hasText(maskedPassword) and hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun clickToggleCardPinAndCvvIcons_shouldIndependentlyToggleVisualTransformationInEditMode() {
        val basePlan = getCardLayoutPlan(withData = true)
        val layoutPlan = basePlan.copy(
            fieldUiState = basePlan.fieldUiState + mapOf(
                FieldId.CARD_PIN to (basePlan.fieldUiState[FieldId.CARD_PIN]?.let { state ->
                    state.copy(
                        cell = state.cell.copy(
                            isPasswordField = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    )
                } ?: FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.pin,
                        isPasswordField = true,
                        visualTransformation = PasswordVisualTransformation()
                    ),
                    data = "1234"
                )),
                FieldId.CARD_CVV to (basePlan.fieldUiState[FieldId.CARD_CVV]?.let { state ->
                    state.copy(
                        cell = state.cell.copy(
                            isPasswordField = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    )
                } ?: FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.cvv,
                        isPasswordField = true,
                        visualTransformation = PasswordVisualTransformation()
                    ),
                    data = "999"
                ))
            )
        )

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.EDIT,
                        layoutPlan = layoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        val toggleDesc = context.getString(R.string.cd_toggle_sensitive_data_visibility)
        val rawCvv = layoutPlan.fieldUiState[FieldId.CARD_CVV]?.data ?: ""
        val rawPin = layoutPlan.fieldUiState[FieldId.CARD_PIN]?.data ?: ""
        val maskedCvv = "\u2022".repeat(rawCvv.length)
        val maskedPin = "\u2022".repeat(rawPin.length)

        // Verify both masked by default
        composeTestRule.onNode(hasText(maskedCvv) and hasSetTextAction()).assertIsDisplayed()
        composeTestRule.onNode(hasText(maskedPin) and hasSetTextAction()).assertIsDisplayed()

        // Toggle all eye icons (PIN and CVV both have eye toggle icons)
        val toggleNodes = composeTestRule.onAllNodesWithContentDescription(toggleDesc)
        assertThat(toggleNodes.fetchSemanticsNodes().size).isAtLeast(2)

        toggleNodes[0].performClick()
        composeTestRule.waitForIdle()
        toggleNodes[1].performClick()
        composeTestRule.waitForIdle()

        // Both unmasked
        composeTestRule.onNode(hasText(rawCvv) and hasSetTextAction()).assertIsDisplayed()
        composeTestRule.onNode(hasText(rawPin) and hasSetTextAction()).assertIsDisplayed()
    }

    // --- CLIPBOARD COPYING IN VIEW MODE across all types ---

    @Test
    fun clickLoginUserIdFieldInViewMode_shouldWriteCorrectTextToClipboard() {
        var clipboardEntry: ClipEntry? = null
        val plan = getLoginLayoutPlan(withData = true)
        val expectedData = plan.fieldUiState[FieldId.LOGIN_USER_ID]?.data ?: ""
        val formattedTextToClick =
            plan.fieldUiState[FieldId.LOGIN_USER_ID]?.getFormattedData() ?: expectedData

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalClipboard provides object : Clipboard {
                    override suspend fun getClipEntry(): ClipEntry? = clipboardEntry
                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
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
                            layoutPlan = plan
                        ),
                        screenAction = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(formattedTextToClick, substring = true).performClick()
        composeTestRule.waitForIdle()
        assertThat(clipboardEntry?.clipData?.getItemAt(0)?.text?.toString()).isEqualTo(expectedData)
    }

    @Test
    fun clickCardNumberFieldInViewMode_shouldWriteCorrectTextToClipboard() {
        var clipboardEntry: ClipEntry? = null
        val basePlan = getCardLayoutPlan(withData = true)
        val plan = basePlan.copy(
            fieldUiState = basePlan.fieldUiState + (
                    FieldId.CARD_NUMBER to (basePlan.fieldUiState[FieldId.CARD_NUMBER]?.let {
                        it.copy(cell = it.cell.copy(visualTransformation = SpaceAfterEveryFourCharsTransformation()))
                    } ?: FieldUiState(data = "4111222233334444"))
                    )
        )
        val expectedData = plan.fieldUiState[FieldId.CARD_NUMBER]?.data ?: ""
        val formattedTextToClick =
            plan.fieldUiState[FieldId.CARD_NUMBER]?.getFormattedData() ?: expectedData

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalClipboard provides object : Clipboard {
                    override suspend fun getClipEntry(): ClipEntry? = clipboardEntry
                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
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
                            layoutPlan = plan
                        ),
                        screenAction = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(formattedTextToClick, substring = true).performClick()
        composeTestRule.waitForIdle()
        assertThat(clipboardEntry?.clipData?.getItemAt(0)?.text?.toString()).isEqualTo(expectedData)
    }

    @Test
    fun clickBankAccountNumberFieldInViewMode_shouldWriteCorrectTextToClipboard() {
        var clipboardEntry: ClipEntry? = null
        val plan = getBankAccountLayoutPlan(withData = true)
        val expectedData = plan.fieldUiState[FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER]?.data ?: ""
        val formattedTextToClick =
            plan.fieldUiState[FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER]?.getFormattedData()
                ?: expectedData

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalClipboard provides object : Clipboard {
                    override suspend fun getClipEntry(): ClipEntry? = clipboardEntry
                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
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
                            layoutPlan = plan
                        ),
                        screenAction = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(formattedTextToClick, substring = true).performClick()
        composeTestRule.waitForIdle()
        assertThat(clipboardEntry?.clipData?.getItemAt(0)?.text?.toString()).isEqualTo(expectedData)
    }

    // --- FORMATTING VISUAL TRANSFORMATIONS IN VIEW MODE ---

    @Test
    fun viewModeBankCard_shouldRenderSpaceAfterEveryFourCharsAndExpiryDateFormatting() {
        val basePlan = getCardLayoutPlan(withData = true)
        val rawNumber = basePlan.fieldUiState[FieldId.CARD_NUMBER]?.data ?: "4111222233334444"
        val rawExpiry = basePlan.fieldUiState[FieldId.CARD_EXPIRY_DATE]?.data ?: "1228"

        val layoutPlan = basePlan.copy(
            fieldUiState = basePlan.fieldUiState + mapOf(
                FieldId.CARD_NUMBER to (basePlan.fieldUiState[FieldId.CARD_NUMBER]?.let {
                    it.copy(cell = it.cell.copy(visualTransformation = SpaceAfterEveryFourCharsTransformation()))
                } ?: FieldUiState(data = rawNumber)),
                FieldId.CARD_EXPIRY_DATE to (basePlan.fieldUiState[FieldId.CARD_EXPIRY_DATE]?.let {
                    it.copy(cell = it.cell.copy(visualTransformation = ExpiryDateTransformation()))
                } ?: FieldUiState(data = rawExpiry))
            )
        )

        val expectedFormattedNumber = rawNumber.chunked(4).joinToString(" ")
        val expectedFormattedExpiry = if (rawExpiry.length >= 2) {
            rawExpiry.substring(0, 2) + "/" + rawExpiry.substring(2)
        } else {
            rawExpiry
        }

        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = layoutPlan
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedFormattedNumber).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedFormattedExpiry).assertIsDisplayed()
    }

    // --- DELETION CONFIRMATION DIALOG ACROSS RECORD TYPES ---

    @Test
    fun clickDeleteButtonAndConfirm_onSecureNoteRecord_shouldEmitOnDeleteClickedAction() {
        var deleteEmitCount = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getNoteLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnDeleteClicked) {
                            deleteEmitCount++
                        }
                    }
                )
            }
        }

        val deleteDesc = context.getString(R.string.cd_action_delete)
        composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()

        assertThat(deleteEmitCount).isEqualTo(1)
    }

    // --- SHARE ACTION PAYLOAD EMISSION ---

    @Test
    fun clickShareButtonOnLoginRecord_shouldEmitOnShareClickedAction() {
        var shareEmitCount = 0
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
                            shareEmitCount++
                        }
                    }
                )
            }
        }

        val shareDesc = context.getString(R.string.cd_action_share)
        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        assertThat(shareEmitCount).isEqualTo(1)
    }

    @Test
    fun clickShareButtonOnSecureNoteRecord_shouldEmitOnShareClickedAction() {
        var shareEmitCount = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getNoteLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnShareClicked) {
                            shareEmitCount++
                        }
                    }
                )
            }
        }

        val shareDesc = context.getString(R.string.cd_action_share)
        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        assertThat(shareEmitCount).isEqualTo(1)
    }

    @Test
    fun clickShareButtonOnBankCardRecord_shouldEmitOnShareClickedAction() {
        var shareEmitCount = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getCardLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnShareClicked) {
                            shareEmitCount++
                        }
                    }
                )
            }
        }

        val shareDesc = context.getString(R.string.cd_action_share)
        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        assertThat(shareEmitCount).isEqualTo(1)
    }

    @Test
    fun clickShareButtonOnBankAccountRecord_shouldEmitOnShareClickedAction() {
        var shareEmitCount = 0
        composeTestRule.setContent {
            SafeBoxTheme {
                SingleRecordScreen(
                    uiState = SingleRecordScreenUiState(
                        isLoading = false,
                        viewMode = ViewMode.VIEW,
                        layoutPlan = getBankAccountLayoutPlan(withData = true)
                    ),
                    screenAction = { action ->
                        if (action is SingleRecordScreenAction.OnShareClicked) {
                            shareEmitCount++
                        }
                    }
                )
            }
        }

        val shareDesc = context.getString(R.string.cd_action_share)
        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        assertThat(shareEmitCount).isEqualTo(1)
    }
}
