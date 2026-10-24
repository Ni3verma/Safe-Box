package com.andryoga.safebox.security

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * [SymmetricKeyUtils] decorator that reports a failed decryption and rethrows it unchanged.
 *
 * Today a stored field that no longer decrypts (corrupt bytes, or ciphertext written under a
 * Keystore key that was since lost and regenerated) is invisible in production. This records that
 * it happened without altering what the caller sees: the same exception, from the same call.
 *
 * Bounded to one event per exception class per process. A list of many unreadable rows would
 * otherwise emit once per row on every list load.
 *
 * @param delegate the real implementation; every call is forwarded to it.
 */
class DecryptFailureReportingSymmetricKeyUtils(
    private val delegate: SymmetricKeyUtils,
    private val analyticsHelper: AnalyticsHelper,
) : SymmetricKeyUtils {

    private val reportedExceptionClasses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun encrypt(data: String): String = delegate.encrypt(data)

    override fun decrypt(data: String): String = try {
        delegate.decrypt(data)
    } catch (e: SessionLockedException) {
        // A locked vault is a session state, not an unreadable row: the data layer fails closed
        // on it by design and it must not count as a decrypt failure.
        throw e
    } catch (e: Exception) {
        report(e)
        throw e
    }

    private fun report(e: Exception) {
        val exceptionClass = e.javaClass.simpleName
        if (!reportedExceptionClasses.add(exceptionClass)) return
        try {
            // The non-fatal carries the stack, which names the DAO and therefore the table; the
            // event carries only the class name. Neither carries ciphertext or plaintext.
            Timber.e(e, "record decrypt failure")
            analyticsHelper.logEvent(AnalyticsKey.RECORD_DECRYPT_FAILURE) {
                param(AnalyticsParam.EXCEPTION, exceptionClass)
            }
        } catch (_: Exception) {
            // a failing reporter must not replace the decryption exception the caller is owed
        }
    }
}
