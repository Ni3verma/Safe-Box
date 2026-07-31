package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.HashingUtils

class FakeHashingUtils : HashingUtils {
    override fun hash(password: String): String {
        return "HASH[$password]|FAKE_SALT"
    }

    override fun compareHash(toCompareText: String, toCompareWithHash: String): Boolean {
        val parts = toCompareWithHash.split("|")
        if (parts.size != 2) {
            throw SecurityException("Invalid hash format")
        }
        return parts[0] == "HASH[$toCompareText]"
    }
}
