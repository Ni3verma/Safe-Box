package com.andryoga.safebox.worker

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ClipboardClearWorker] covering verified clip wipe, foreign clip skip,
 * Android 10+ background clipboard read restriction fallback, and missing system service handling.
 */
class ClipboardClearWorkerTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var analyticsHelper: FakeAnalyticsHelper

    @Before
    fun setUp() {
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        context = mockk(relaxed = true)
        clipboardManager = mockk(relaxed = true)
        analyticsHelper = FakeAnalyticsHelper()
        every { context.applicationContext } returns context
        every { context.getSystemService(ClipboardManager::class.java) } returns clipboardManager
    }

    @After
    fun tearDown() {
        unmockkStatic(ClipData::class)
    }

    private fun buildWorker(clipId: String? = "expected-clip-id"): ClipboardClearWorker {
        val inputData = Data.Builder().apply {
            if (clipId != null) {
                putString(CommonConstants.CLIPBOARD_CLIP_ID, clipId)
            }
        }.build()

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return ClipboardClearWorker(
                    context = appContext,
                    params = workerParameters,
                    analyticsHelper = analyticsHelper,
                )
            }
        }

        return TestListenableWorkerBuilder<ClipboardClearWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }

    @Test
    fun doWork_whenClipboardManagerIsNull_returnsSuccessWithoutLoggingAnalytics() = runTest {
        every { context.getSystemService(ClipboardManager::class.java) } returns null

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun doWork_whenClipDescriptionMatchesExpectedId_clearsClipboardAndLogsVerifiedEvent() = runTest {
        val extras = mockk<PersistableBundle>()
        val description = mockk<ClipDescription>()
        every { extras.getString(CommonConstants.CLIPBOARD_CLIP_ID) } returns "expected-clip-id"
        every { description.extras } returns extras
        every { clipboardManager.primaryClipDescription } returns description

        val result = buildWorker("expected-clip-id").doWork()

        assertThat(result).isEqualTo(Result.success())
        verifyClipboardCleared()
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)
        assertThat(event.params[AnalyticsParam.RESULT.paramName]).isEqualTo("verified")
    }

    @Test
    fun doWork_whenClipDescriptionDoesNotMatchExpectedId_skipsClearAndLogsSkippedEvent() = runTest {
        val extras = mockk<PersistableBundle>()
        val description = mockk<ClipDescription>()
        every { extras.getString(CommonConstants.CLIPBOARD_CLIP_ID) } returns "different-clip-id"
        every { description.extras } returns extras
        every { clipboardManager.primaryClipDescription } returns description

        val result = buildWorker("expected-clip-id").doWork()

        assertThat(result).isEqualTo(Result.success())
        verify(exactly = 0) { clipboardManager.clearPrimaryClip() }
        verify(exactly = 0) { clipboardManager.setPrimaryClip(any()) }
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CLIPBOARD_AUTO_CLEAR_SKIPPED)).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)).isFalse()
    }

    @Test
    fun doWork_whenClipDescriptionIsNullInBackground_clearsClipboardBlindlyAndLogsUnverifiedEvent() = runTest {
        every { clipboardManager.primaryClipDescription } returns null

        val result = buildWorker("expected-clip-id").doWork()

        assertThat(result).isEqualTo(Result.success())
        verifyClipboardCleared()
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)
        assertThat(event.params[AnalyticsParam.RESULT.paramName]).isEqualTo("unverified")
    }

    @Test
    fun doWork_whenReadingClipDescriptionThrowsSecurityException_clearsClipboardBlindlyAndLogsUnverifiedEvent() = runTest {
        every { clipboardManager.primaryClipDescription } throws SecurityException("Background clipboard read denied")

        val result = buildWorker("expected-clip-id").doWork()

        assertThat(result).isEqualTo(Result.success())
        verifyClipboardCleared()
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)
        assertThat(event.params[AnalyticsParam.RESULT.paramName]).isEqualTo("unverified")
    }

    /**
     * The clear itself can be refused by the system, for example by a SecurityException on OEM
     * builds that restrict clipboard writes from background work.
     *
     * This is the last line of defence against a copied password sitting on the clipboard
     * indefinitely, so the worker has to report failure and let WorkManager retry. Swallowing the
     * exception and returning success would mark the credential as cleared while it is still there.
     */
    @Test
    fun doWork_whenClearingTheClipboardThrows_returnsFailureSoTheWipeIsRetried() = runTest {
        every { clipboardManager.primaryClipDescription } returns null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            every { clipboardManager.clearPrimaryClip() } throws SecurityException("clear denied")
        } else {
            every { clipboardManager.setPrimaryClip(any()) } throws SecurityException("clear denied")
        }

        val result = buildWorker("expected-clip-id").doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)).isFalse()
    }

    private fun verifyClipboardCleared() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
        } else {
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
        }
    }
}
