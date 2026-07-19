package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils

class FakeSymmetricKeyUtils : SymmetricKeyUtils {
    override fun encrypt(data: String): String {
        return "ENC[$data]"
    }

    override fun decrypt(data: String): String {
        return if (data.startsWith("ENC[") && data.endsWith("]")) {
            data.substring(4, data.length - 1)
        } else {
            data
        }
    }
}
