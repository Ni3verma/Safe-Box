package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.security.interfaces.SessionLockedException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * The fail-closed contract of every secure-DAO `Flow`: a vault that is locked while
 * the list is being decrypted yields an empty list instead of an exception, so a `stateIn`
 * pipeline in a ViewModel survives a lock without crashing. Anything else still propagates; a
 * broken row is a bug to surface, not a state to hide.
 *
 * The flow completes after the empty emission. The next unlock navigates to a fresh Home, whose
 * ViewModel subscribes again.
 */
internal fun <T> Flow<List<T>>.emptyWhenSessionLocked(): Flow<List<T>> = catch { cause ->
    if (cause is SessionLockedException) emit(emptyList()) else throw cause
}
