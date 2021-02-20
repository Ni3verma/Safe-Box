package com.andryoga.safebox.security

import javax.crypto.SecretKey

interface KeyStoreUtils {
    fun getSecretKey(): SecretKey
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}