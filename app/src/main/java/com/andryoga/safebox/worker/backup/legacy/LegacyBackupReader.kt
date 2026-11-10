package com.andryoga.safebox.worker.backup.legacy

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.worker.backup.DecodedBackup
import com.andryoga.safebox.worker.backup.OpenedBackup
import com.andryoga.safebox.worker.backup.decodeAuthenticatorsLeniently
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.ByteArrayInputStream
import java.io.InvalidClassException
import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Reads backup formats 1–3: a Java-serialised map of byte arrays in which every record type is
 * encrypted separately by [PasswordBasedEncryption] under one salt and IV.
 *
 * Nothing writes this format any more; it stays so files users already hold remain restorable.
 * The password is used raw, without NFKC, because these files were written without it.
 */
class LegacyBackupReader @Inject constructor(
    private val passwordBasedEncryption: PasswordBasedEncryption,
) {
    /**
     * Deserialises the container through a class allow-list and reads its version.
     *
     * @param bytes The whole file.
     * @return The container, records still encrypted.
     * @throws InvalidClassException When the stream names a class outside the allow-list.
     * @throws InvalidObjectException When the top-level object is not a map.
     * @throws NullPointerException When the version entry is missing.
     */
    fun open(bytes: ByteArray): OpenedBackup {
        val map = readContainer(bytes)
        return LegacyBackup(map, readBackupVersion(map))
    }

    @Suppress("UNCHECKED_CAST")
    private fun readContainer(bytes: ByteArray): Map<String, ByteArray?> {
        val secureObjectInputStream = object : ObjectInputStream(ByteArrayInputStream(bytes)) {
            override fun resolveClass(desc: ObjectStreamClass): Class<*> {
                if (desc.name !in ALLOWED_CLASSES) {
                    throw InvalidClassException("Unauthorized deserialization attempt", desc.name)
                }
                return super.resolveClass(desc)
            }
        }
        return secureObjectInputStream.use {
            val fileObject = it.readObject()
            if (fileObject !is Map<*, *>) {
                throw InvalidObjectException("input file is not correct, was not able to read it is as Map")
            }
            fileObject as Map<String, ByteArray?>
        }
    }

    /**
     * Reads the backup format version from the header.
     *
     * The version is a single byte written with `toByte()`, so it is read back as **unsigned**:
     * a signed read would turn `0x80`..`0xFF` negative and let those versions pass the
     * newer-version check. `0xFF` is reserved as the marker for a future multi-byte version,
     * see persistence-and-crypto.md.
     *
     * @param map Deserialized backup header and payload map.
     * @return Version in `0..255`.
     * @throws NullPointerException if the version key is missing.
     * @throws IndexOutOfBoundsException if the version entry is empty.
     */
    private fun readBackupVersion(map: Map<String, ByteArray?>): Int {
        return map[CommonConstants.VERSION_KEY]!![0].toUByte().toInt()
    }

    private inner class LegacyBackup(
        private val map: Map<String, ByteArray?>,
        override val version: Int,
    ) : OpenedBackup {
        /**
         * Decrypts every record payload the map carries. Each type is decrypted only when its
         * entry is present and non-null, so a map without any payload decrypts nothing and
         * proves nothing about the password; [DecodedBackup.hasNoRecordPayload] reports it.
         */
        override fun decode(password: CharArray): DecodedBackup {
            val creationDateBytes = map[CommonConstants.CREATION_DATE_KEY]!!
            // Check size for backward compatibility: new backups store an 8-byte Long timestamp,
            // while legacy backups stored a 1-byte value. Calling ByteBuffer.wrap().long on <8 bytes
            // throws BufferUnderflowException and fails the restore.
            val createdAt = if (creationDateBytes.size >= Long.SIZE_BYTES) {
                ByteBuffer.wrap(creationDateBytes).long
            } else {
                creationDateBytes[0].toLong()
            }
            val salt = map[CommonConstants.SALT_KEY]!!
            val iv = map[CommonConstants.IV_KEY]!!
            fun decrypt(payload: ByteArray): String =
                String(passwordBasedEncryption.decrypt(password, payload, salt, iv))

            fun <T> decryptList(key: String, serializer: KSerializer<T>): List<T>? =
                map[key]?.let { Json.decodeFromString(ListSerializer(serializer), decrypt(it)) }

            val authenticators = map[CommonConstants.AUTHENTICATOR_DATA_KEY]?.let {
                Json.decodeAuthenticatorsLeniently(Json.parseToJsonElement(decrypt(it)).jsonArray)
            }
            return DecodedBackup(
                createdAt = createdAt,
                logins = decryptList(CommonConstants.LOGIN_DATA_KEY, ExportLoginData.serializer()),
                bankAccounts = decryptList(
                    CommonConstants.BANK_ACCOUNT_DATA_KEY,
                    ExportBankAccountData.serializer(),
                ),
                bankCards = decryptList(CommonConstants.BANK_CARD_DATA_KEY, ExportBankCardData.serializer()),
                secureNotes = decryptList(
                    CommonConstants.SECURE_NOTE_DATA_KEY,
                    ExportSecureNoteData.serializer(),
                ),
                authenticators = authenticators?.records,
                undecodableAuthenticatorCount = authenticators?.undecodableCount ?: 0,
            )
        }
    }

    companion object {
        private val ALLOWED_CLASSES = setOf(
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.Map",
            "java.lang.String",
            "[B",
            "java.lang.Number",
            "java.lang.Integer",
            "java.lang.Long",
        )

        /** `STREAM_MAGIC` and `STREAM_VERSION`, the first four bytes of every Java serialisation stream. */
        private val STREAM_HEADER = byteArrayOf(0xAC.toByte(), 0xED.toByte(), 0x00, 0x05)

        /**
         * @param bytes The start of a file.
         * @return True when [bytes] start like a Java serialisation stream, as formats 1–3 do.
         */
        fun hasStreamHeader(bytes: ByteArray): Boolean =
            bytes.size >= STREAM_HEADER.size &&
                bytes.copyOfRange(0, STREAM_HEADER.size).contentEquals(STREAM_HEADER)
    }
}
