package com.andryoga.composeapp.security.interfaces

interface PasswordBasedEncryption {
    fun encryptDecrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
        encrypt: Boolean
    ): ByteArray

    fun getRandomSalt(): ByteArray
    fun getRandomIV(): ByteArray
}
