package com.andryoga.safebox.worker.backup.legacy

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.test.fixtures.LegacyBackupCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InvalidClassException
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.util.Date
import javax.crypto.BadPaddingException

/** Real legacy crypto end to end: files are built the way format 1–3 builds wrote them. */
class LegacyBackupReaderTest {

    private val reader = LegacyBackupReader(PasswordBasedEncryptionImpl())

    @Test
    fun openThenDecode_formatThreeFile_returnsRecordsAndVersion() {
        val logins = listOf(ExportLoginData("Login", null, "pwd", "n", "user", 1L, 2L))
        val bytes = serialise(
            baseMap(version = 3) + mapOf(
                CommonConstants.LOGIN_DATA_KEY to encrypt(Json.encodeToString(ListSerializer(ExportLoginData.serializer()), logins)),
                CommonConstants.AUTHENTICATOR_DATA_KEY to null,
            ),
        )

        val opened = reader.open(bytes)
        val decoded = opened.decode(PASSWORD.toCharArray())

        assertThat(opened.version).isEqualTo(3)
        assertThat(decoded.logins).isEqualTo(logins)
        assertThat(decoded.authenticators).isNull()
        assertThat(decoded.bankCards).isNull()
    }

    @Test
    fun decode_rawPasswordIsUsedWithoutNormalisation() {
        // Written precomposed; the same text decomposed is a different legacy password.
        val bytes = serialise(baseMap(version = 3) + mapOf(CommonConstants.SECURE_NOTE_DATA_KEY to encrypt("[]", "caf\u00e9")))

        assertThrows(BadPaddingException::class.java) {
            reader.open(bytes).decode("cafe\u0301".toCharArray())
        }
        assertThat(reader.open(bytes).decode("caf\u00e9".toCharArray()).secureNotes).isEmpty()
    }

    @Test
    fun decode_formatOneSingleByteCreationDate_isReadWithoutFailing() {
        val bytes = serialise(
            baseMap(version = 1) + mapOf(
                CommonConstants.CREATION_DATE_KEY to byteArrayOf(7),
                CommonConstants.SECURE_NOTE_DATA_KEY to encrypt("[]"),
            ),
        )

        assertThat(reader.open(bytes).decode(PASSWORD.toCharArray()).createdAt).isEqualTo(7L)
    }

    @Test
    fun decode_authenticatorWithUnknownAlgorithm_isCountedAndDropped() {
        val json = """[{"title":"A","secretKey":"JBSWY3DPEHPK3PXP","creationDate":1,"updateDate":1,""" +
            """"algorithm":"BLAKE2B","digits":6,"period":30}]"""
        val bytes = serialise(baseMap(version = 3) + mapOf(CommonConstants.AUTHENTICATOR_DATA_KEY to encrypt(json)))

        val decoded = reader.open(bytes).decode(PASSWORD.toCharArray())

        assertThat(decoded.authenticators).isEmpty()
        assertThat(decoded.undecodableAuthenticatorCount).isEqualTo(1)
    }

    @Test
    fun decode_withoutAnyPayload_decryptsNothingAndHasNoRecordPayload() {
        val decoded = reader.open(serialise(baseMap(version = 3))).decode("any password".toCharArray())

        assertThat(decoded.hasNoRecordPayload).isTrue()
    }

    @Test
    fun open_versionByteWithHighBitSet_isReadUnsigned() {
        val bytes = serialise(baseMap(version = 0x80.toByte()))

        assertThat(reader.open(bytes).version).isEqualTo(128)
    }

    /** The committed files written by the old builds themselves, through the narrowed allow-list. */
    @Test
    fun openThenDecode_committedFormatOneToThreeFixtures_returnTheirRecords() {
        val fixtures = listOf(
            Triple("format-1.bak", 1, "Upgrade@@Test123"),
            Triple("format-2.bak", 2, "Fixture@Backup1"),
            Triple("format-3.bak", 3, "Upgrade@@Test123"),
        )
        for ((name, version, password) in fixtures) {
            val opened = reader.open(File(FIXTURES_DIR, name).readBytes())
            val decoded = opened.decode(password.toCharArray())

            assertThat(opened.version).isEqualTo(version)
            assertThat(decoded.logins).isNotEmpty()
            assertThat(decoded.hasNoRecordPayload).isFalse()
        }
    }

    @Test
    fun open_streamNamingAClassOutsideTheAllowList_isRefused() {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(Date(0)) }

        assertThrows(InvalidClassException::class.java) {
            reader.open(output.toByteArray())
        }
    }

    @Test
    fun hasStreamHeader_onlyForJavaSerialisation() {
        assertThat(LegacyBackupReader.hasStreamHeader(serialise(baseMap(version = 3)))).isTrue()
        assertThat(LegacyBackupReader.hasStreamHeader("SBOX".toByteArray())).isFalse()
        assertThat(LegacyBackupReader.hasStreamHeader(byteArrayOf(0xAC.toByte()))).isFalse()
    }

    private fun encrypt(json: String, password: String = PASSWORD): ByteArray =
        LegacyBackupCipher.encrypt(password.toCharArray(), json.toByteArray())

    private fun baseMap(version: Byte): Map<String, ByteArray?> = mapOf(
        CommonConstants.VERSION_KEY to byteArrayOf(version),
        CommonConstants.SALT_KEY to LegacyBackupCipher.FIXED_SALT,
        CommonConstants.IV_KEY to LegacyBackupCipher.FIXED_IV,
        CommonConstants.CREATION_DATE_KEY to ByteBuffer.allocate(Long.SIZE_BYTES).putLong(1_000L).array(),
    )

    /** Serialises like BackupDataWorker did: a LinkedHashMap from Kotlin's mutableMapOf. */
    private fun serialise(map: Map<String, ByteArray?>): ByteArray {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(LinkedHashMap(map)) }
        return output.toByteArray()
    }

    private companion object {
        const val PASSWORD = "Legacy@Password1"

        /** Unit tests run with the module directory as the working directory. */
        const val FIXTURES_DIR = "../upgrade-test/fixtures"
    }
}
