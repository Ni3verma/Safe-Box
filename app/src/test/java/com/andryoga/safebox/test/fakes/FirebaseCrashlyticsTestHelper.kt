package com.andryoga.safebox.test.fakes

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

object FirebaseCrashlyticsTestHelper {
    fun setupMockCrashlytics(): FirebaseCrashlytics {
        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics
        return mockCrashlytics
    }

    fun tearDownMockCrashlytics() {
        unmockkStatic(FirebaseCrashlytics::class)
    }
}
