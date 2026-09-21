package com.andryoga.safebox.ui.core

import android.content.ClipDescription
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.test.fakes.FakeClipboard
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [rememberCopyToClipboardAction], the single funnel every copy affordance in the app
 * goes through.
 *
 * Two security properties live here and nowhere else, which is why they are worth pinning:
 * the clip must be marked sensitive so the Android 13+ clipboard preview redacts it instead of
 * rendering the password in clear text over the launcher, and every copy must schedule the
 * automatic wipe so a credential cannot sit on the clipboard indefinitely. Both fail silently if
 * they regress, with no visible symptom until the secret is already exposed.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard = FakeClipboard()

    @Before
    fun setup() {
        // The action schedules the clear through WorkManager.getInstance. The manifest removes
        // WorkManagerInitializer, so the test harness has to stand one up. SynchronousExecutor
        // also keeps the scheduled clear out of the app's real work database.
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @Test
    fun copyToClipboard_shouldMarkTheClipSensitiveSoTheSystemPreviewRedactsIt() {
        copy(label = "Password", value = "hunter2")

        val description = clipboard.lastClipEntry?.clipData?.description
        assertThat(description).isNotNull()
        assertThat(description!!.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)).isTrue()
        assertThat(clipboard.lastCopiedText).isEqualTo("hunter2")
    }

    @Test
    fun copyToClipboard_shouldTagTheClipWithAnIdAndScheduleTheAutomaticClear() {
        copy(label = "Password", value = "hunter2")

        val clipId = clipboard.lastClipEntry
            ?.clipData
            ?.description
            ?.extras
            ?.getString(CommonConstants.CLIPBOARD_CLIP_ID)
        // The worker refuses to wipe a clip it cannot identify as this app's, so a missing id
        // would quietly turn the auto-clear into a no-op.
        assertThat(clipId).isNotEmpty()

        assertThat(enqueuedClearWork()).hasSize(1)
    }

    @Test
    fun copyTwice_shouldReplaceThePendingClearRatherThanQueueASecondOne() {
        val copy = givenCopyAction()
        copy("Password", "first")
        copy("Password", "second")

        // Unique work with REPLACE means the countdown restarts from the latest copy. Queuing a
        // second request instead would let the earlier timer wipe the newer clip early.
        assertThat(enqueuedClearWork()).hasSize(1)
        assertThat(clipboard.lastCopiedText).isEqualTo("second")
    }

    /**
     * Renders a host exposing the production copy action.
     *
     * The composition is created once per test because `setContent` may only be called a single
     * time on a Compose test rule, so the returned lambda is what tests invoke repeatedly.
     *
     * @return Callable that performs a copy with the given label and value.
     */
    private fun givenCopyAction(): (String, String) -> Unit {
        var copyAction: ((String, String, String) -> Unit)? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                SafeBoxTheme {
                    copyAction = rememberCopyToClipboardAction()
                }
            }
        }
        return { label, value ->
            composeTestRule.runOnIdle { copyAction!!(label, value, "Copied") }
            composeTestRule.waitForIdle()
        }
    }

    /**
     * Convenience for tests that only need a single copy.
     *
     * @param label Clipboard entry label.
     * @param value Value to copy.
     */
    private fun copy(label: String, value: String) {
        givenCopyAction()(label, value)
    }

    /**
     * @return Work entries currently queued under the unique clipboard-clear name, excluding any
     * that already ran to completion.
     */
    private fun enqueuedClearWork(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CommonConstants.WORKER_NAME_CLEAR_CLIPBOARD)
            .get()
            .filter { !it.state.isFinished }
}
