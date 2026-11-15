package com.andryoga.safebox.security.envelope.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VaultMigrationProgressTest {

    @Test
    fun startThenReset_shouldPublishRunningThenIdle() {
        val progress = VaultMigrationProgress()
        assertThat(progress.state.value).isEqualTo(MigrationProgressState.Idle)

        progress.start()
        assertThat(progress.state.value).isEqualTo(MigrationProgressState.Running)

        progress.reset()
        assertThat(progress.state.value).isEqualTo(MigrationProgressState.Idle)
    }
}
