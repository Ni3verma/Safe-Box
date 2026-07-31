package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

class FakeSymmetricKeyUtils(
    private val prefix: String = "ENC[",
    private val suffix: String = if (prefix == "ENC[") "]" else ""
) : SymmetricKeyUtils {
    override fun encrypt(data: String): String {
        return "$prefix$data$suffix"
    }

    override fun decrypt(data: String): String {
        return if (data.startsWith(prefix) && data.endsWith(suffix)) {
            val endIdx = if (suffix.isEmpty()) data.length else data.length - suffix.length
            data.substring(prefix.length, endIdx)
        } else {
            data
        }
    }
}
