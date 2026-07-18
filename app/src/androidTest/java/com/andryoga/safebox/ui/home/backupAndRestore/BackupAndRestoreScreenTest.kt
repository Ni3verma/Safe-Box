package com.andryoga.safebox.ui.home.backupAndRestore

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.home.backupAndRestore.components.BackupView
import com.andryoga.safebox.ui.home.backupAndRestore.components.RestoreView
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.EnterPasswordView
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.Operation
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.WorkflowState
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.dialogBodyText
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component and dialog workflow UI test suite for BackupView, RestoreView, and NewBackupOrRestoreScreen dialogs across all states.
 */
@RunWith(AndroidJUnit4::class)
class BackupAndRestoreScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupStateLoading_shouldDisplayCircularProgressIndicator() {
        composeTestRule.setContent {
            SafeBoxTheme {
                BackupView(
                    backupState = Loading,
                    launchSelectBackupPath = {},
                    onScreenAction = {}
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Companion.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun backupStatePathNotSet_shouldRenderWarningIconAndSetLocationButton() {
        var setLocationClicked = false
        composeTestRule.setContent {
            SafeBoxTheme {
                BackupView(
                    backupState = BackupPathNotSet(),
                    launchSelectBackupPath = { setLocationClicked = true },
                    onScreenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.backup_path_not_set_message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.backup_set_location))
            .performClick()
        assertThat(setLocationClicked).isTrue()
    }

    @Test
    fun backupStatePathSet_shouldRenderConfiguredPathTimestampAndActionButtons() {
        val testPath = "/tree/primary:Safebox backups"
        val testTime = "18 Jul 2026 05:30 PM"
        var newBackupClicked = false
        var editPathClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                BackupView(
                    backupState = BackupPathSet(backupPath = testPath, backupTime = testTime),
                    launchSelectBackupPath = { editPathClicked = true },
                    onScreenAction = { action ->
                        if (action is ScreenAction.NewBackupClick) {
                            newBackupClicked = true
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.backup_set_message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.backup_path, testPath))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.backup_time, testTime))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.backup_edit_path)).performClick()
        assertThat(editPathClicked).isTrue()

        composeTestRule.onNode(hasText(context.getString(R.string.backup)) and hasClickAction())
            .performClick()
        assertThat(newBackupClicked).isTrue()
    }

    @Test
    fun restoreView_shouldRenderRestoreInfoAndLaunchPickerOnClick() {
        var restorePickerLaunched = false
        composeTestRule.setContent {
            SafeBoxTheme {
                RestoreView(
                    launchRestoreFilePicker = { restorePickerLaunched = true }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.restore_info)).assertIsDisplayed()
        composeTestRule.onNode(hasText(context.getString(R.string.restore)) and hasClickAction())
            .performClick()
        assertThat(restorePickerLaunched).isTrue()
    }

    @Test
    fun enterPasswordView_inAskForPasswordState_shouldMaskInputAndToggleVisibility() {
        var passwordInput = "SecretBackup123"
        composeTestRule.setContent {
            SafeBoxTheme {
                EnterPasswordView(
                    operation = Operation.Backup,
                    workflowState = WorkflowState.ASK_FOR_PASSWORD,
                    password = passwordInput,
                    onPasswordChange = { passwordInput = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.new_backup_dialog_body_text))
            .assertIsDisplayed()

        val toggleDesc = context.getString(R.string.cd_toggle_sensitive_data_visibility)
        val maskedText = "\u2022".repeat(passwordInput.length)

        composeTestRule.onNode(hasText(maskedText) and hasSetTextAction()).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(toggleDesc).performClick()
        composeTestRule.onNode(hasText(passwordInput) and hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun enterPasswordView_inWrongPasswordState_shouldShowSupportingErrorTextAndRedOutline() {
        composeTestRule.setContent {
            SafeBoxTheme {
                EnterPasswordView(
                    operation = Operation.Restore(null),
                    workflowState = WorkflowState.WRONG_PASSWORD,
                    password = "wrongpass",
                    onPasswordChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message))
            .assertIsDisplayed()
    }

    @Test
    fun enterPasswordView_inFailedState_shouldShowSupportingFailedErrorText() {
        composeTestRule.setContent {
            SafeBoxTheme {
                EnterPasswordView(
                    operation = Operation.Backup,
                    workflowState = WorkflowState.FAILED,
                    password = "failedpass",
                    onPasswordChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.failed_message))
            .assertIsDisplayed()
    }

    @Test
    fun dialogBodyText_inInProgressState_shouldRenderInProgressMessage() {
        composeTestRule.setContent {
            SafeBoxTheme {
                dialogBodyText(
                    operation = Operation.Backup,
                    workflowState = WorkflowState.IN_PROGRESS,
                    password = "",
                    onPasswordChange = {}
                ).invoke()
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.backup_in_progress_message))
            .assertIsDisplayed()
    }

    @Test
    fun dialogBodyText_inSuccessState_shouldRenderCompleteMessage() {
        composeTestRule.setContent {
            SafeBoxTheme {
                dialogBodyText(
                    operation = Operation.Restore(null),
                    workflowState = WorkflowState.SUCCESS,
                    password = "",
                    onPasswordChange = {}
                ).invoke()
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.restore_complete_message))
            .assertIsDisplayed()
    }
}
