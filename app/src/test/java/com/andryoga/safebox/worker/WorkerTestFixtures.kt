package com.andryoga.safebox.worker

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer

/**
 * Reusable test doubles and fixture builders for Background Worker unit tests.
 */

class FakePasswordBasedEncryption(
    var saltToReturn: ByteArray = ByteArray(16) { 1 },
    var ivToReturn: ByteArray = ByteArray(16) { 2 },
    var shouldThrowBadPadding: Boolean = false
) : PasswordBasedEncryption {

    override fun encryptDecrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
        encrypt: Boolean
    ): ByteArray {
        if (shouldThrowBadPadding) {
            throw javax.crypto.BadPaddingException("Given final block not properly padded")
        }
        return data.copyOf()
    }

    override fun getRandomSalt(): ByteArray = saltToReturn.copyOf()
    override fun getRandomIV(): ByteArray = ivToReturn.copyOf()
}

object WorkerTestFixtures {

    fun createBackupMap(
        salt: ByteArray = ByteArray(16) { 1 },
        iv: ByteArray = ByteArray(16) { 2 },
        version: Byte = CommonConstants.BACKUP_VERSION.toByte(),
        creationDate: Long = System.currentTimeMillis(),
        loginData: ByteArray? = null,
        bankAccountData: ByteArray? = null,
        bankCardData: ByteArray? = null,
        secureNoteData: ByteArray? = null
    ): Map<String, ByteArray?> {
        val map = mutableMapOf<String, ByteArray?>()
        map[CommonConstants.SALT_KEY] = salt
        map[CommonConstants.IV_KEY] = iv
        map[CommonConstants.VERSION_KEY] = ByteArray(1) { version }
        map[CommonConstants.CREATION_DATE_KEY] =
            ByteBuffer.allocate(Long.SIZE_BYTES).putLong(creationDate).array()
        loginData?.let { map[CommonConstants.LOGIN_DATA_KEY] = it }
        bankAccountData?.let { map[CommonConstants.BANK_ACCOUNT_DATA_KEY] = it }
        bankCardData?.let { map[CommonConstants.BANK_CARD_DATA_KEY] = it }
        secureNoteData?.let { map[CommonConstants.SECURE_NOTE_DATA_KEY] = it }
        return map
    }

    fun writeBackupMapToFile(file: File, map: Map<String, ByteArray?>) {
        file.parentFile?.mkdirs()
        ObjectOutputStream(FileOutputStream(file)).use {
            it.writeObject(map)
        }
    }
}
