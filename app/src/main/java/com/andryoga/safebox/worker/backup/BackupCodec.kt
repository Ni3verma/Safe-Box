package com.andryoga.safebox.worker.backup

import androidx.annotation.Keep
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportBackupPayload
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.PasswordKdf
import com.andryoga.safebox.security.envelope.models.KdfParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Writes backup format [CommonConstants.BACKUP_VERSION] and reads every format from
 * [FIRST_VERSION] on: they share this container, so a format bump for a payload change (a new
 * optional field) needs no new codec. Only a different container gets its own reader, as formats
 * 1–3 have `LegacyBackupReader`. A version newer than the build is refused by the restore before
 * [OpenedBackup.decode].
 *
 * Layout: magic `SBOX` | version | header length `n` (2 bytes, big-endian) | header JSON
 * (`n` bytes, readable without the password) | key check | body. The key check is
 * `iv ‖ AES-256-GCM tag over an empty plaintext` and the body is `iv ‖ AES-256-GCM(payload JSON)`;
 * both bind bytes `0 until 7 + n` as AAD, so the header cannot be edited unnoticed. The key comes
 * from [PasswordKdf] with the parameters the header names, so the file is self-describing.
 */
object BackupCodec {
    /** First format written in this container. */
    const val FIRST_VERSION = 4

    /**
     * Largest file a restore reads into memory; a 2 000-record vault is about 2 MB. Stops a picked
     * file that is not a backup (a video, say) from exhausting the heap before it can be rejected.
     */
    const val MAX_FILE_BYTES = 64 * 1024 * 1024

    /**
     * Most PBKDF2 iterations a header may ask for: ten times today's setting leaves room to raise it
     * while a crafted header cannot stall the restore for minutes before the key check rejects it.
     */
    const val MAX_KDF_ITERATIONS = 10 * PasswordKdf.PBKDF2_ITERATIONS

    private val MAGIC = "SBOX".toByteArray(Charsets.US_ASCII)
    private const val VERSION_OFFSET = 4
    private const val HEADER_LENGTH_OFFSET = 5
    private const val HEADER_OFFSET = 7
    private const val UNSIGNED_SHORT_MASK = 0xFFFF
    private const val IV_BYTES = AesGcmWrap.IV_SIZE_BYTES
    private const val TAG_BYTES = AesGcmWrap.GCM_TAG_BITS / 8
    private const val KEY_CHECK_BYTES = IV_BYTES + TAG_BYTES
    private const val KEY_ALGORITHM_AES = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AUTHENTICATORS_FIELD = "authenticators"

    private val headerJson = Json { ignoreUnknownKeys = true }

    /** Payload JSON, as strict as the format-1–3 reader: an unknown record field fails the restore. */
    private val payloadJson = Json

    /**
     * @param bytes The start of a file.
     * @return True when [bytes] start with the container's magic.
     */
    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /**
     * Encrypts [payload] into a complete file of format [CommonConstants.BACKUP_VERSION].
     *
     * @param password Backup password; not modified, the caller zeroes it.
     * @param payload Every record type, each list filled (empty when the vault has none).
     * @param createdAt Epoch milliseconds stored in the header.
     * @param appVersionCode `versionCode` of the writing build, stored in the header.
     * @param kdfParams Key-derivation parameters; a fresh salt at [PasswordKdf.PBKDF2_ITERATIONS]
     * unless a test needs cheaper ones.
     * @return The file's bytes.
     */
    fun encode(
        password: CharArray,
        payload: ExportBackupPayload,
        createdAt: Long,
        appVersionCode: Int,
        kdfParams: KdfParams = PasswordKdf.newParams(),
    ): ByteArray {
        val headerBytes = headerJson.encodeToString(
            Header.serializer(),
            Header(createdAt = createdAt, appVersionCode = appVersionCode, kdf = kdfParams.encode()),
        ).toByteArray(Charsets.UTF_8)
        check(headerBytes.size <= UNSIGNED_SHORT_MASK) { "backup header too large: ${headerBytes.size}" }
        val prefix = ByteBuffer.allocate(HEADER_OFFSET + headerBytes.size)
            .put(MAGIC)
            .put(CommonConstants.BACKUP_VERSION.toByte())
            .putShort(headerBytes.size.toShort())
            .put(headerBytes)
            .array()
        val payloadBytes = payloadJson.encodeToString(ExportBackupPayload.serializer(), payload)
            .toByteArray(Charsets.UTF_8)
        val key = PasswordKdf.deriveKey(password.copyOf(), kdfParams)
        try {
            return prefix + seal(key, ByteArray(0), prefix) + seal(key, payloadBytes, prefix)
        } finally {
            key.fill(0)
            payloadBytes.fill(0)
        }
    }

    /**
     * Reads the version byte; everything else is parsed by [OpenedBackup.decode], so a newer
     * version whose header changed shape is still reported as too new rather than as corrupt.
     *
     * @param bytes The whole file, starting with the magic.
     * @return The file, records still encrypted.
     * @throws CorruptBackupException When the file is too short to hold a version.
     */
    fun open(bytes: ByteArray): OpenedBackup {
        if (!hasMagic(bytes) || bytes.size <= VERSION_OFFSET) {
            throw CorruptBackupException("not an SBOX backup")
        }
        return ContainerBackup(bytes, bytes[VERSION_OFFSET].toUByte().toInt())
    }

    private class ContainerBackup(
        private val bytes: ByteArray,
        override val version: Int,
    ) : OpenedBackup {
        override fun decode(password: CharArray): DecodedBackup {
            if (version < FIRST_VERSION) throw CorruptBackupException("version $version predates this container")
            if (bytes.size < HEADER_OFFSET) throw CorruptBackupException("truncated before the header")
            val headerLength =
                ByteBuffer.wrap(bytes, HEADER_LENGTH_OFFSET, 2).short.toInt() and UNSIGNED_SHORT_MASK
            val keyCheckOffset = HEADER_OFFSET + headerLength
            val bodyOffset = keyCheckOffset + KEY_CHECK_BYTES
            val bodyLength = bytes.size - bodyOffset
            if (bodyLength < IV_BYTES + TAG_BYTES) throw CorruptBackupException("truncated before the body")
            val prefix = bytes.copyOfRange(0, keyCheckOffset)
            val header = parseHeader(prefix)
            val kdfParams = try {
                KdfParams.decode(header.kdf)
            } catch (e: IllegalArgumentException) {
                throw CorruptBackupException("unreadable KDF parameters", e)
            }
            // The header is checked only after the key exists, so bound the work it can ask for.
            if (kdfParams.iterations > MAX_KDF_ITERATIONS) {
                throw CorruptBackupException("KDF iterations ${kdfParams.iterations} over the limit")
            }
            val key = PasswordKdf.deriveKey(password.copyOf(), kdfParams)
            try {
                // A wrong password fails here with AEADBadTagException; it propagates, and
                // RestoreDataWorker's BadPaddingException catch reports INCORRECT_PASSWORD.
                openSealed(key, bytes, keyCheckOffset, KEY_CHECK_BYTES, prefix)
                val payload = try {
                    openSealed(key, bytes, bodyOffset, bodyLength, prefix)
                } catch (e: AEADBadTagException) {
                    throw CorruptBackupException("body failed authentication after the key check passed", e)
                }
                try {
                    return parsePayload(header.createdAt, payload)
                } finally {
                    payload.fill(0)
                }
            } finally {
                key.fill(0)
            }
        }
    }

    private fun parseHeader(prefix: ByteArray): Header = try {
        val text = prefix.decodeToString(HEADER_OFFSET, prefix.size, throwOnInvalidSequence = true)
        headerJson.decodeFromString(Header.serializer(), text)
    } catch (e: CharacterCodingException) {
        throw CorruptBackupException("header is not UTF-8", e)
    } catch (e: SerializationException) {
        throw CorruptBackupException("unreadable header", e)
    }

    private fun parsePayload(createdAt: Long, payload: ByteArray): DecodedBackup {
        val fields = payloadJson.parseToJsonElement(payload.decodeToString()).jsonObject
        val strict = payloadJson.decodeFromJsonElement(
            ExportBackupPayload.serializer(),
            JsonObject(fields - AUTHENTICATORS_FIELD),
        )
        val authenticators = fields[AUTHENTICATORS_FIELD]
            ?.takeUnless { it is JsonNull }
            ?.let { payloadJson.decodeAuthenticatorsLeniently(it.jsonArray) }
        return DecodedBackup(
            createdAt = createdAt,
            logins = strict.logins,
            bankAccounts = strict.bankAccounts,
            bankCards = strict.bankCards,
            secureNotes = strict.secureNotes,
            authenticators = authenticators?.records,
            undecodableAuthenticatorCount = authenticators?.undecodableCount ?: 0,
        )
    }

    /** @return `iv ‖ ciphertext ‖ tag` of [plaintext] under [key], bound to [aad]. */
    private fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM_AES))
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Expected $IV_BYTES-byte GCM IV, got ${iv.size}" }
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Opens the `iv ‖ ciphertext ‖ tag` section at [offset] of [source].
     *
     * @return The plaintext.
     * @throws AEADBadTagException When the key, the AAD or the section is wrong.
     */
    private fun openSealed(
        key: ByteArray,
        source: ByteArray,
        offset: Int,
        length: Int,
        aad: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM_AES),
            GCMParameterSpec(AesGcmWrap.GCM_TAG_BITS, source, offset, IV_BYTES),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(source, offset + IV_BYTES, length - IV_BYTES)
    }

    /**
     * The unencrypted header.
     *
     * @property kdf [KdfParams] in their wire form.
     */
    @Keep
    @Serializable
    private data class Header(
        val createdAt: Long,
        val appVersionCode: Int,
        val kdf: String,
    )
}
