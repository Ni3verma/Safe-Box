package com.andryoga.safebox.security

interface KeyStoreUtils {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}