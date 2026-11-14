package com.andryoga.safebox.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class BackupPasswordRegistryTest {

    private val registry = BackupPasswordRegistry()

    @Test
    fun take_afterPut_shouldReturnThePasswordForThatWorkId() {
        val workId = UUID.randomUUID()
        registry.put(workId, "Qwerty@@123")

        assertThat(registry.take(workId)).isEqualTo("Qwerty@@123".toCharArray())
    }

    @Test
    fun take_shouldRemoveTheEntry() {
        val workId = UUID.randomUUID()
        registry.put(workId, "Qwerty@@123")
        registry.take(workId)

        assertThat(registry.take(workId)).isNull()
    }

    @Test
    fun take_forAnUnknownWorkId_shouldReturnNull() {
        // The process-death case: WorkManager replays the request into a registry that never saw it.
        registry.put(UUID.randomUUID(), "Qwerty@@123")

        assertThat(registry.take(UUID.randomUUID())).isNull()
    }

    @Test
    fun put_twiceForTheSameWorkId_shouldKeepTheLatest() {
        val workId = UUID.randomUUID()
        registry.put(workId, "first")
        registry.put(workId, "second")

        assertThat(registry.take(workId)).isEqualTo("second".toCharArray())
    }
}
