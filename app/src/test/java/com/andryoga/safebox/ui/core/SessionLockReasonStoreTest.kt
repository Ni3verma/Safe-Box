package com.andryoga.safebox.ui.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionLockReasonStoreTest {

    private val store = SessionLockReasonStore()

    @Test
    fun consume_whenNothingWasRecorded_shouldReturnNull() {
        assertThat(store.consume()).isNull()
    }

    @Test
    fun consume_afterRecord_shouldReturnTheReasonExactlyOnce() {
        store.record(SessionLockReason.AWAY_TIMEOUT)

        assertThat(store.consume()).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
        // A second login screen must not read the reason the first one already showed.
        assertThat(store.consume()).isNull()
    }

    @Test
    fun record_twiceBeforeConsume_shouldKeepTheLatestReason() {
        store.record(SessionLockReason.AWAY_TIMEOUT)
        store.record(SessionLockReason.AWAY_TIMEOUT)

        assertThat(store.consume()).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
        assertThat(store.consume()).isNull()
    }
}
