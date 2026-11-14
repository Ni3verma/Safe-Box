package com.andryoga.safebox.security.envelope.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VaultMigrationProgressTest {

    @Test
    fun fraction_duringTheDecryptPass_shouldFillTheDecryptShareOnly() {
        val halfway = MigrationProgressState.Running(done = 5, total = 10, phase = MigrationPhase.DECRYPTING)
        val decrypted = MigrationProgressState.Running(done = 10, total = 10, phase = MigrationPhase.DECRYPTING)

        assertThat(halfway.fraction).isWithin(TOLERANCE).of(0.45f)
        assertThat(decrypted.fraction).isWithin(TOLERANCE).of(MigrationProgressState.Running.DECRYPT_SHARE)
    }

    @Test
    fun fraction_duringTheSavePass_shouldContinueFromTheDecryptShareToFull() {
        val started = MigrationProgressState.Running(done = 0, total = 10, phase = MigrationPhase.SAVING)
        val halfway = MigrationProgressState.Running(done = 5, total = 10, phase = MigrationPhase.SAVING)
        val saved = MigrationProgressState.Running(done = 10, total = 10, phase = MigrationPhase.SAVING)

        assertThat(started.fraction).isWithin(TOLERANCE).of(0.9f)
        assertThat(halfway.fraction).isWithin(TOLERANCE).of(0.95f)
        assertThat(saved.fraction).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun fraction_forAnEmptyVault_shouldBeCompleteForItsPhase() {
        val empty = MigrationProgressState.Running(done = 0, total = 0, phase = MigrationPhase.DECRYPTING)

        assertThat(empty.fraction).isWithin(TOLERANCE).of(MigrationProgressState.Running.DECRYPT_SHARE)
    }

    @Test
    fun fraction_whilePreparing_shouldBeZero() {
        val preparing = MigrationProgressState.Running(done = 0, total = 10, phase = MigrationPhase.PREPARING)

        assertThat(preparing.fraction).isEqualTo(0f)
    }

    @Test
    fun update_shouldPublishThePhaseAndCounts() {
        val progress = VaultMigrationProgress()

        progress.start(4)
        assertThat(progress.state.value)
            .isEqualTo(MigrationProgressState.Running(done = 0, total = 4, phase = MigrationPhase.PREPARING))

        progress.update(MigrationPhase.SAVING, 2, 4)
        assertThat(progress.state.value)
            .isEqualTo(MigrationProgressState.Running(done = 2, total = 4, phase = MigrationPhase.SAVING))

        progress.reset()
        assertThat(progress.state.value).isEqualTo(MigrationProgressState.Idle)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
