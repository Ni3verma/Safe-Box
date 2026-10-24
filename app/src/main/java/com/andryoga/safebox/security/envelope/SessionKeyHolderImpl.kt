package com.andryoga.safebox.security.envelope

import androidx.annotation.VisibleForTesting
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe in-memory implementation of [SessionKeyHolder].
 *
 * State transitions and key-buffer snapshotting are synchronized so a concurrent [clearSessionKey]
 * zeroes the holder's internal byte array without corrupting an in-flight [SecretKeySpec] already
 * handed to [withSessionKey].
 */
@Singleton
class SessionKeyHolderImpl @Inject constructor() : SessionKeyHolder {

    private sealed interface State {
        data object Locked : State
        class Unlocked(val dekBytes: ByteArray) : State
        // TODO(D3): legacy vault (crypto_version = 1) only
        class LegacyUnlocked(val legacyKey: SecretKey) : State
    }

    private val lock = Any()

    @Volatile
    private var state: State = State.Locked

    private val _lockEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val lockEvents: SharedFlow<Unit> = _lockEvents.asSharedFlow()

    // Written together with `state` under the lock, so an observer never sees them disagree.
    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // TODO(D3): legacy vault (crypto_version = 1) only
    override val isLegacySession: Boolean
        get() = state is State.LegacyUnlocked

    override fun <T> withSessionKey(block: (SecretKey) -> T): T {
        val sessionKey: SecretKey = synchronized(lock) {
            when (val current = state) {
                State.Locked -> throw SessionLockedException()
                is State.Unlocked -> SecretKeySpec(current.dekBytes, KEY_ALGORITHM_AES)
                is State.LegacyUnlocked -> current.legacyKey
            }
        }
        return block(sessionKey)
    }

    override fun setSessionKey(dekBytes: ByteArray) {
        // The DEK is itself an AES-256 key: it opens dek_verifier and every record field.
        require(dekBytes.size == AesGcmWrap.KEY_SIZE_BYTES) {
            "DEK must be ${AesGcmWrap.KEY_SIZE_BYTES} bytes, got ${dekBytes.size}"
        }
        val copy = dekBytes.copyOf()
        synchronized(lock) {
            zeroIfUnlocked(state)
            state = State.Unlocked(copy)
            _isUnlocked.value = true
        }
    }

    // TODO(D3): legacy vault (crypto_version = 1) only
    override fun setLegacySession(legacyKey: SecretKey) {
        synchronized(lock) {
            zeroIfUnlocked(state)
            state = State.LegacyUnlocked(legacyKey)
            _isUnlocked.value = true
        }
    }

    override fun clearSessionKey() {
        synchronized(lock) {
            zeroIfUnlocked(state)
            state = State.Locked
            _isUnlocked.value = false
        }
        _lockEvents.tryEmit(Unit)
    }

    /**
     * Returns the raw internal DEK buffer reference so unit tests can verify in-place zeroing after
     * [clearSessionKey].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun peekInternalBufferForTesting(): ByteArray? = synchronized(lock) {
        (state as? State.Unlocked)?.dekBytes
    }

    private fun zeroIfUnlocked(previous: State) {
        if (previous is State.Unlocked) {
            previous.dekBytes.fill(0)
        }
    }

    private companion object {
        const val KEY_ALGORITHM_AES = "AES"
    }
}
