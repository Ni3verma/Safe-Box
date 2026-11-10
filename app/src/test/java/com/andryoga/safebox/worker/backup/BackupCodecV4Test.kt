package com.andryoga.safebox.worker.backup

import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportBackupPayload
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.security.envelope.PasswordKdf
import com.andryoga.safebox.security.envelope.models.KdfParams
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

class BackupCodecV4Test {

    private val payload = ExportBackupPayload(
        logins = listOf(ExportLoginData("Login", "https://a.example", "pwd", "multi\nline \"note\" \\", "user", 1L, 2L)),
        bankAccounts = listOf(
            ExportBankAccountData("Bank", "123", "Name", null, "", null, null, null, null, null, 3L, 4L),
        ),
        bankCards = listOf(ExportBankCardData("Card", "Name", "4111", "1234", "123", "1230", null, 5L, 6L)),
        secureNotes = listOf(ExportSecureNoteData("Note", "emoji 🔐 עברית é", 7L, 8L)),
        authenticators = listOf(TestFixtures.createTestExportAuthenticatorData(title = "2FA")),
    )

    @Test
    fun encodeThenDecode_returnsEveryRecordAndTheHeaderTime() {
        val bytes = encode(payload, createdAt = 1_700_000_000_000L)

        val decoded = BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())

        assertThat(decoded.createdAt).isEqualTo(1_700_000_000_000L)
        assertThat(decoded.logins).isEqualTo(payload.logins)
        assertThat(decoded.bankAccounts).isEqualTo(payload.bankAccounts)
        assertThat(decoded.bankCards).isEqualTo(payload.bankCards)
        assertThat(decoded.secureNotes).isEqualTo(payload.secureNotes)
        assertThat(decoded.authenticators).isEqualTo(payload.authenticators)
        assertThat(decoded.undecodableAuthenticatorCount).isEqualTo(0)
    }

    @Test
    fun encode_writesMagicVersionAndAReadableHeaderWithTheKdfParameters() {
        val kdfParams = PasswordKdf.newParams(iterations = TEST_ITERATIONS)
        val bytes = encode(payload, kdfParams = kdfParams)

        assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("SBOX")
        assertThat(bytes[4]).isEqualTo(4.toByte())
        val headerLength = ByteBuffer.wrap(bytes, 5, 2).short.toInt()
        val header = String(bytes, 7, headerLength, Charsets.UTF_8)
        assertThat(header).contains("\"kdf\":\"${kdfParams.encode()}\"")
        assertThat(header).contains("\"appVersionCode\":$APP_VERSION_CODE")
        // Nothing about the records may leak into the plaintext part.
        assertThat(String(bytes, Charsets.ISO_8859_1)).doesNotContain("Login")
    }

    @Test
    fun encode_twiceWithTheSameInput_producesDifferentFiles() {
        val kdfParams = PasswordKdf.newParams(iterations = TEST_ITERATIONS)

        val first = encode(payload, kdfParams = kdfParams)
        val second = encode(payload, kdfParams = kdfParams)

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun encode_doesNotModifyThePassword() {
        val password = PASSWORD.toCharArray()

        BackupCodecV4.encode(password, payload, 0L, APP_VERSION_CODE, PasswordKdf.newParams(iterations = TEST_ITERATIONS))

        assertThat(String(password)).isEqualTo(PASSWORD)
    }

    @Test
    fun decode_doesNotModifyThePassword() {
        val bytes = encode(payload)
        val password = PASSWORD.toCharArray()

        BackupCodecV4.open(bytes).decode(password)

        assertThat(String(password)).isEqualTo(PASSWORD)
    }

    @Test
    fun decode_passwordInADifferentUnicodeForm_unlocksTheFile() {
        // "é" precomposed when written, "e" + combining acute when typed again: NFKC makes them one.
        val bytes = BackupCodecV4.encode(
            "caf\u00e9".toCharArray(),
            payload,
            0L,
            APP_VERSION_CODE,
            PasswordKdf.newParams(iterations = TEST_ITERATIONS),
        )

        val decoded = BackupCodecV4.open(bytes).decode("cafe\u0301".toCharArray())

        assertThat(decoded.logins).isEqualTo(payload.logins)
    }

    @Test
    fun decode_wrongPassword_throwsBadPaddingFromTheKeyCheck() {
        val bytes = encode(payload)

        val thrown = assertThrows(BadPaddingException::class.java) {
            BackupCodecV4.open(bytes).decode("wrong".toCharArray())
        }

        assertThat(thrown).isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun decode_tamperedBody_throwsCorruptNotWrongPassword() {
        val bytes = encode(payload)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()

        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun decode_editedHeader_failsTheKeyCheck() {
        val bytes = encode(payload, createdAt = 1_000_000_000_000L)
        // Same length, different digit: the header is bound as AAD.
        val headerText = String(bytes, Charsets.ISO_8859_1)
        val index = headerText.indexOf("1000000000000")
        bytes[index] = '2'.code.toByte()

        assertThrows(BadPaddingException::class.java) {
            BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun open_newerVersion_reportsItWithoutParsingTheRest() {
        val bytes = "SBOX".toByteArray() + 5.toByte() + byteArrayOf(0x7F, 0x7F)

        assertThat(BackupCodecV4.open(bytes).version).isEqualTo(5)
    }

    @Test
    fun open_versionByteWithHighBitSet_isReadUnsigned() {
        val bytes = "SBOX".toByteArray() + 0x80.toByte()

        assertThat(BackupCodecV4.open(bytes).version).isEqualTo(128)
    }

    @Test
    fun decode_olderVersionInsideTheNewContainer_throwsCorrupt() {
        val bytes = encode(payload)
        bytes[4] = 3

        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun decode_headerLengthPastTheEndOfTheFile_throwsCorruptBeforeKeyDerivation() {
        val bytes = encode(payload)
        ByteBuffer.wrap(bytes, 5, 2).putShort(0xFFFF.toShort())

        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun decode_truncatedFile_throwsCorrupt() {
        val bytes = encode(payload)

        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open(bytes.copyOf(40)).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun decode_unreadableKdfParameters_throwsCorrupt() {
        val bytes = encode(payload)
        val text = String(bytes, Charsets.ISO_8859_1)
        bytes[text.indexOf("v1|")] = 'x'.code.toByte()

        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())
        }
    }

    @Test
    fun open_withoutMagic_throwsCorrupt() {
        assertThrows(CorruptBackupException::class.java) {
            BackupCodecV4.open("SBO".toByteArray())
        }
    }

    @Test
    fun hasMagic_onlyForTheFormatFourPrefix() {
        assertThat(BackupCodecV4.hasMagic("SBOX".toByteArray())).isTrue()
        assertThat(BackupCodecV4.hasMagic(byteArrayOf(0xAC.toByte(), 0xED.toByte(), 0, 5))).isFalse()
        assertThat(BackupCodecV4.hasMagic("SB".toByteArray())).isFalse()
    }

    @Test
    fun decode_payloadWithoutATypeField_returnsNullForThatType() {
        val bytes = encode(ExportBackupPayload(logins = payload.logins))

        val decoded = BackupCodecV4.open(bytes).decode(PASSWORD.toCharArray())

        assertThat(decoded.logins).isEqualTo(payload.logins)
        assertThat(decoded.authenticators).isNull()
        assertThat(decoded.secureNotes).isNull()
        assertThat(decoded.hasNoRecordPayload).isFalse()
    }

    @Test
    fun decode_emptyPayload_hasNoRecordPayload() {
        val decoded = BackupCodecV4.open(encode(ExportBackupPayload())).decode(PASSWORD.toCharArray())

        assertThat(decoded.hasNoRecordPayload).isTrue()
    }

    /** The worker reports and gates on BACKUP_VERSION; the file must carry the same number. */
    @Test
    fun version_isTheFormatTheAppDeclares() {
        assertThat(BackupCodecV4.VERSION).isEqualTo(CommonConstants.BACKUP_VERSION)
    }

    @Test
    fun kdfParams_fromTheDefaultEncode_useProductionStrength() {
        val bytes = BackupCodecV4.encode(PASSWORD.toCharArray(), ExportBackupPayload(), 0L, APP_VERSION_CODE)
        val headerLength = ByteBuffer.wrap(bytes, 5, 2).short.toInt()
        val kdf = Regex("\"kdf\":\"([^\"]+)\"").find(String(bytes, 7, headerLength, Charsets.UTF_8))!!.groupValues[1]

        assertThat(KdfParams.decode(kdf).iterations).isEqualTo(PasswordKdf.PBKDF2_ITERATIONS)
    }

    private fun encode(
        payload: ExportBackupPayload,
        createdAt: Long = 0L,
        kdfParams: KdfParams = PasswordKdf.newParams(iterations = TEST_ITERATIONS),
    ): ByteArray = BackupCodecV4.encode(PASSWORD.toCharArray(), payload, createdAt, APP_VERSION_CODE, kdfParams)

    private companion object {
        const val PASSWORD = "Backup@Password1"
        const val APP_VERSION_CODE = 42
        const val TEST_ITERATIONS = 1_000
    }
}
