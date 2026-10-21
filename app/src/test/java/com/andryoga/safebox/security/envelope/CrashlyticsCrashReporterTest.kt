package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.test.fakes.FirebaseCrashlyticsTestHelper
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrashlyticsCrashReporterTest {

    private lateinit var mockCrashlytics: FirebaseCrashlytics
    private lateinit var reporter: CrashlyticsCrashReporter

    @Before
    fun setUp() {
        mockCrashlytics = FirebaseCrashlyticsTestHelper.setupMockCrashlytics()
        reporter = CrashlyticsCrashReporter()
    }

    @After
    fun tearDown() {
        FirebaseCrashlyticsTestHelper.tearDownMockCrashlytics()
    }

    @Test
    fun setKeyBreadcrumbAndRecordNonFatal_shouldForwardCallsToFirebaseCrashlytics() {
        val failure = IllegalStateException("migration failure")

        reporter.setKey("migration_stage", "reencrypt")
        reporter.setKey("migration_attempt", 2)
        reporter.breadcrumb("migration: stage=reencrypt table=login_data rows=12")
        reporter.recordNonFatal(failure)

        verify(exactly = 1) { mockCrashlytics.setCustomKey("migration_stage", "reencrypt") }
        verify(exactly = 1) { mockCrashlytics.setCustomKey("migration_attempt", 2) }
        verify(exactly = 1) {
            mockCrashlytics.log("migration: stage=reencrypt table=login_data rows=12")
        }
        verify(exactly = 1) { mockCrashlytics.recordException(failure) }
    }

    @Test
    fun allMethods_whenCrashlyticsUnavailable_shouldNotThrow() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } throws
            IllegalStateException("FirebaseApp is not initialized")

        reporter.setKey("migration_stage", "commit")
        reporter.setKey("migration_attempt", 1)
        reporter.breadcrumb("migration: stage=commit")
        reporter.recordNonFatal(RuntimeException("failure"))
    }
}
