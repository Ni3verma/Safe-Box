package com.andryoga.safebox.ui.core.appupdate

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Covers every Play result and install status that [AppUpdateStateMapper] can receive, including
 * the cold start mid-download case where Play reports an in-progress update as
 * [AppUpdateResult.Available].
 */
class AppUpdateStateMapperTest {

    private val appUpdateManager: AppUpdateManager = mockk()

    @Test
    fun notAvailableResult_shouldMapToNotAvailable() {
        assertThat(AppUpdateStateMapper.map(AppUpdateResult.NotAvailable))
            .isEqualTo(AppUpdateState.NotAvailable)
    }

    @Test
    fun availableFlexibleUpdate_shouldMapToAvailableWithVersionCode() {
        val result = availableResult(updateInfo(versionCode = 42))

        assertThat(AppUpdateStateMapper.map(result)).isEqualTo(AppUpdateState.Available(42))
    }

    @Test
    fun availableButFlexibleNotAllowed_shouldMapToNotAvailable() {
        val result = availableResult(updateInfo(isFlexibleAllowed = false))

        assertThat(AppUpdateStateMapper.map(result)).isEqualTo(AppUpdateState.NotAvailable)
    }

    @Test
    fun developerTriggeredUpdateWithoutActiveInstall_shouldMapToNotAvailable() {
        val result = availableResult(
            updateInfo(availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS),
        )

        assertThat(AppUpdateStateMapper.map(result)).isEqualTo(AppUpdateState.NotAvailable)
    }

    @Test
    fun availableWhileInstallInProgress_shouldMapToDownloading() {
        IN_PROGRESS_STATUSES.forEach { status ->
            val result = availableResult(
                updateInfo(
                    availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                    status = status,
                    isFlexibleAllowed = false,
                ),
            )

            assertWithMessage("install status $status")
                .that(AppUpdateStateMapper.map(result))
                .isEqualTo(AppUpdateState.Downloading)
        }
    }

    @Test
    fun availableWithDownloadedStatus_shouldMapToDownloaded() {
        val result = availableResult(updateInfo(status = InstallStatus.DOWNLOADED))

        assertThat(AppUpdateStateMapper.map(result)).isEqualTo(AppUpdateState.Downloaded)
    }

    @Test
    fun availableAfterCanceledDownload_shouldMapToAvailable() {
        val result = availableResult(updateInfo(status = InstallStatus.CANCELED, versionCode = 7))

        assertThat(AppUpdateStateMapper.map(result)).isEqualTo(AppUpdateState.Available(7))
    }

    @Test
    fun inProgressActiveStatus_shouldMapToDownloading() {
        IN_PROGRESS_STATUSES.forEach { status ->
            assertWithMessage("install status $status")
                .that(AppUpdateStateMapper.map(inProgressResult(status)))
                .isEqualTo(AppUpdateState.Downloading)
        }
    }

    @Test
    fun inProgressTerminalFailureStatus_shouldMapToNotAvailable() {
        listOf(
            InstallStatus.FAILED,
            InstallStatus.CANCELED,
            InstallStatus.UNKNOWN,
            InstallStatus.INSTALLED,
            InstallStatus.REQUIRES_UI_INTENT,
        ).forEach { status ->
            assertWithMessage("install status $status")
                .that(AppUpdateStateMapper.map(inProgressResult(status)))
                .isEqualTo(AppUpdateState.NotAvailable)
        }
    }

    @Test
    fun inProgressDownloadedStatus_shouldMapToDownloaded() {
        assertThat(AppUpdateStateMapper.map(inProgressResult(InstallStatus.DOWNLOADED)))
            .isEqualTo(AppUpdateState.Downloaded)
    }

    @Test
    fun downloadedResult_shouldMapToDownloaded() {
        assertThat(AppUpdateStateMapper.map(AppUpdateResult.Downloaded(appUpdateManager)))
            .isEqualTo(AppUpdateState.Downloaded)
    }

    private fun availableResult(info: AppUpdateInfo) =
        AppUpdateResult.Available(appUpdateManager, info)

    private fun inProgressResult(status: Int): AppUpdateResult {
        val installState = mockk<InstallState> { every { installStatus() } returns status }
        return AppUpdateResult.InProgress(installState)
    }

    private fun updateInfo(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        status: Int = InstallStatus.UNKNOWN,
        isFlexibleAllowed: Boolean = true,
        versionCode: Int = 1,
    ): AppUpdateInfo = mockk {
        every { updateAvailability() } returns availability
        every { installStatus() } returns status
        every { isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) } returns isFlexibleAllowed
        every { availableVersionCode() } returns versionCode
    }

    private companion object {
        val IN_PROGRESS_STATUSES = listOf(
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            InstallStatus.INSTALLING,
        )
    }
}
