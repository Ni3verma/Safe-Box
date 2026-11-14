package com.andryoga.safebox.ui.core

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why the vault session was closed, for the login screen that follows: the HomeGraph gate
 * navigates on every clear, so the navigation itself cannot say why. One value per lock source
 * that Login explains with a banner; the envelope MRs 5 and 6 add theirs.
 */
enum class SessionLockReason {
    /** The away timeout cleared an open session. */
    AWAY_TIMEOUT,
}

/**
 * Process-memory hand-off of a [SessionLockReason] from whoever clears the session key to the next
 * login screen. Not persisted: a reason that outlived the process would greet a cold start with a
 * banner about a session the user never saw end.
 */
@Singleton
class SessionLockReasonStore @Inject constructor() {
    private val reason = AtomicReference<SessionLockReason?>(null)

    /** Records [lockReason]; a later record replaces it until the next [consume]. */
    fun record(lockReason: SessionLockReason) {
        reason.set(lockReason)
    }

    /** Returns and clears the recorded reason, so exactly one login screen sees it; `null` if none. */
    fun consume(): SessionLockReason? = reason.getAndSet(null)
}
