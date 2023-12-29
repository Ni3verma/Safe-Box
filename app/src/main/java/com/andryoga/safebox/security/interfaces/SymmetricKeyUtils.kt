package com.andryoga.safebox.security.interfaces

interface SymmetricKeyUtils {
    fun encrypt(data: String): String

    fun decrypt(data: String): String
}
