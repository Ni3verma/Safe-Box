package com.andryoga.safebox.ui.home.backupAndRestore

import androidx.lifecycle.SavedStateHandle
import com.andryoga.safebox.ui.home.navigation.HomeRouteType.BackupAndRestoreRoute
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.spyk
import org.junit.Test

class BackupAndRestoreRouteProviderTest {
    /** `toRoute()` needs a real navigation bundle, so the route itself is stubbed; the handle is real. */
    private fun provider(
        savedStateHandle: SavedStateHandle,
        startWithRestoreWorkflow: Boolean,
    ): BackupAndRestoreRouteProvider =
        spyk(BackupAndRestoreRouteProvider(savedStateHandle)).also {
            every { it.getRoute() } returns BackupAndRestoreRoute(startWithRestoreWorkflow)
        }

    @Test
    fun `consumeStartWithRestoreWorkflow is true only the first time`() {
        val provider = provider(SavedStateHandle(), startWithRestoreWorkflow = true)

        assertThat(provider.consumeStartWithRestoreWorkflow()).isTrue()
        assertThat(provider.consumeStartWithRestoreWorkflow()).isFalse()
    }

    @Test
    fun `consumeStartWithRestoreWorkflow stays consumed for a new provider over the same handle`() {
        val savedStateHandle = SavedStateHandle()
        provider(savedStateHandle, startWithRestoreWorkflow = true).consumeStartWithRestoreWorkflow()

        val recreated = provider(savedStateHandle, startWithRestoreWorkflow = true)

        assertThat(recreated.consumeStartWithRestoreWorkflow()).isFalse()
    }

    @Test
    fun `consumeStartWithRestoreWorkflow is false when the route does not ask for it`() {
        val provider = provider(SavedStateHandle(), startWithRestoreWorkflow = false)

        assertThat(provider.consumeStartWithRestoreWorkflow()).isFalse()
        assertThat(provider.consumeStartWithRestoreWorkflow()).isFalse()
    }
}
