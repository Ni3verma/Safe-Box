package com.andryoga.safebox.worker

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportBackupPayload
import com.andryoga.safebox.security.envelope.PasswordKdf
import com.andryoga.safebox.worker.backup.BackupCodec
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer

/**
 * Fixture builders for the backup worker unit tests.
 */
object WorkerTestFixtures {

    /** Last format of the Java-serialised map container. */
    const val LAST_LEGACY_VERSION: Byte = 3

    /** Iterations for test-written format-4 files; production strength only slows the suite. */
    private const val TEST_KDF_ITERATIONS = 1_000

    fun createBackupMap(
        salt: ByteArray = ByteArray(16) { 1 },
        iv: ByteArray = ByteArray(16) { 2 },
        version: Byte = LAST_LEGACY_VERSION,
        creationDate: Long = System.currentTimeMillis(),
        loginData: ByteArray? = null,
        bankAccountData: ByteArray? = null,
        bankCardData: ByteArray? = null,
        secureNoteData: ByteArray? = null,
        authenticatorData: ByteArray? = null,
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
        authenticatorData?.let { map[CommonConstants.AUTHENTICATOR_DATA_KEY] = it }
        return map
    }

    fun writeBackupMapToFile(file: File, map: Map<String, ByteArray?>) {
        file.parentFile?.mkdirs()
        ObjectOutputStream(FileOutputStream(file)).use {
            it.writeObject(map)
        }
    }

    /**
     * Writes a real format-4 file, at test-only KDF strength.
     *
     * @return The file's bytes, for tests that tamper with them.
     */
    fun writeFormatFourBackup(
        file: File,
        password: String,
        payload: ExportBackupPayload,
        createdAt: Long = System.currentTimeMillis(),
    ): ByteArray {
        val bytes = BackupCodec.encode(
            password = password.toCharArray(),
            payload = payload,
            createdAt = createdAt,
            appVersionCode = 1,
            kdfParams = PasswordKdf.newParams(iterations = TEST_KDF_ITERATIONS),
        )
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return bytes
    }
}
