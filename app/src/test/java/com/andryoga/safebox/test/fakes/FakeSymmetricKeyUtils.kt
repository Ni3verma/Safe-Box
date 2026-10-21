package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

class FakeSymmetricKeyUtils(
    private val prefix: String = "ENC[",
    private val suffix: String = if (prefix == "ENC[") "]" else ""
) : SymmetricKeyUtils {
    /** When set, every [encrypt] and [decrypt] throws it instead: a locked vault or a broken row. */
    var failure: Throwable? = null

    override fun encrypt(data: String): String {
        failure?.let { throw it }
        return "$prefix$data$suffix"
    }

    override fun decrypt(data: String): String {
        failure?.let { throw it }
        return if (data.startsWith(prefix) && data.endsWith(suffix)) {
            val endIdx = if (suffix.isEmpty()) data.length else data.length - suffix.length
            data.substring(prefix.length, endIdx)
        } else {
            data
        }
    }
}
