package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import javax.crypto.AEADBadTagException

class SessionLockedFlowsTest {

    @Test
    fun emptyWhenSessionLocked_whenUpstreamThrowsSessionLocked_emitsEmptyListAndCompletes() = runTest {
        val upstream = flow<List<String>> {
            emit(listOf("a", "b"))
            throw SessionLockedException()
        }

        upstream.emptyWhenSessionLocked().test {
            assertThat(awaitItem()).containsExactly("a", "b")
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun emptyWhenSessionLocked_whenUpstreamThrowsAnythingElse_rethrowsIt() = runTest {
        val failure = AEADBadTagException("row 7 does not decrypt")
        val upstream = flow<List<String>> { throw failure }

        upstream.emptyWhenSessionLocked().test {
            assertThat(awaitError()).isSameInstanceAs(failure)
        }
    }

    @Test
    fun emptyWhenSessionLocked_whenUpstreamIsHealthy_passesEveryEmissionThrough() = runTest {
        val upstream = flow {
            emit(listOf(1))
            emit(listOf(1, 2))
        }

        upstream.emptyWhenSessionLocked().test {
            assertThat(awaitItem()).containsExactly(1)
            assertThat(awaitItem()).containsExactly(1, 2)
            awaitComplete()
        }
    }
}
