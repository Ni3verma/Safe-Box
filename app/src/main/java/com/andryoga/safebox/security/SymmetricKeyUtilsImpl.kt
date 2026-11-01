package com.andryoga.safebox.security

import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import javax.inject.Inject

/**
 * Field encryption for the record tables under whatever key the current session holds.
 *
 * The key is never stored here: every call asks [SessionKeyHolder] for it, so a locked vault fails
 * closed with [SessionLockedException] on both directions. The data layer does not know whether
 * it received the DEK or the legacy Keystore handle of a fallback session; both go through
 * [AesGcmFieldCipher].
 */
class SymmetricKeyUtilsImpl
@Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder,
) : SymmetricKeyUtils {
    object Constants {
        const val separator = AesGcmFieldCipher.SEPARATOR
        const val gcmAuthTagLength = AesGcmFieldCipher.GCM_AUTH_TAG_LENGTH_BITS
    }

    override fun encrypt(data: String): String =
        sessionKeyHolder.withSessionKey { key -> AesGcmFieldCipher.encrypt(data, key) }

    override fun decrypt(data: String): String =
        sessionKeyHolder.withSessionKey { key -> AesGcmFieldCipher.decrypt(data, key) }
}
