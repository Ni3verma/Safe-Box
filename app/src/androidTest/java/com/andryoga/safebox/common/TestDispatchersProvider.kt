package com.andryoga.safebox.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import javax.inject.Inject

/**
 * Test double implementing [DispatchersProvider] backed by a [TestDispatcher]
 * for synchronized virtual-time execution during Hilt instrumented testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatchersProvider @Inject constructor(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : DispatchersProvider {
    override val main: CoroutineDispatcher get() = testDispatcher
    override val default: CoroutineDispatcher get() = testDispatcher
    override val io: CoroutineDispatcher get() = testDispatcher
}
