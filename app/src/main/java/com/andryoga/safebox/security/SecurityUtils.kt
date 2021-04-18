package com.andryoga.safebox.security

import android.util.Base64

class SecurityUtils {
    companion object {
        fun encodeBase64(byteArray: ByteArray): String {
            return Base64.encodeToString(byteArray, Base64.DEFAULT)
        }

        fun decodeBase64(encodedString: String): ByteArray {
            return Base64.decode(encodedString, Base64.DEFAULT)
        }
    }
}