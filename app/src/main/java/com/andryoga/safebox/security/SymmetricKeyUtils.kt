package com.andryoga.safebox.security

interface SymmetricKeyUtils {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}