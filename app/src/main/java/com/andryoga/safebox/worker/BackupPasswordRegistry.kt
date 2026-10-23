package com.andryoga.safebox.worker

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a backup or restore password to the worker that runs the request, in process memory only,
 * so it never reaches WorkManager's database. Process death empties it and a replayed request
 * fails as `SESSION_LOCKED`; a lock clears it together with the work.
 *
 * Keyed by request id, not a single slot: the auto-backup enqueued at login and a restore or
 * manual backup confirmed a moment later are different requests (`APPEND_OR_REPLACE` can hold
 * two of the same name), and a worker must never take a password typed for another one.
 */
@Singleton
class BackupPasswordRegistry @Inject constructor() {
    private val passwords = ConcurrentHashMap<UUID, CharArray>()

    /** Stores a copy of [password] for [workId]; an earlier entry for the same id is zeroed. */
    fun put(workId: UUID, password: String) {
        passwords.put(workId, password.toCharArray())?.fill(NUL)
    }

    /** Removes and returns the password for [workId] (the caller zeroes it), or `null` if none. */
    fun take(workId: UUID): CharArray? = passwords.remove(workId)

    /** Zeroes and drops every password still registered. */
    fun clear() {
        for (workId in passwords.keys()) {
            passwords.remove(workId)?.fill(NUL)
        }
    }

    private companion object {
        const val NUL = '\u0000'
    }
}
