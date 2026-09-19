package com.andryoga.safebox.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SafeBoxDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_1_2_shouldMigrateLoginCardAndBankAccountTablesWithoutDataLoss() {
        var db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO login_data (`title`, `url`, `password`, `notes`, `userId`, `creationDate`, `updateDate`) " +
                    "VALUES ('Apple ID', 'apple.com', 'secret', 'my note', 'user@apple.com', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_card_data (`title`, `name`, `number`, `pin`, `cvv`, `expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Visa Card', 'John Doe', '12345678', '1234', '999', '12/28', 'Card notes', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_account_data (`title`, `accountNumber`, `customerName`, `customerId`, `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Checking Account', '9876543210', 'John Doe', 'C-1', 'B-1', 'Main', 'Address', 'IFSC001', 'MICR001', 'Account notes', 1000, 2000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration.MIGRATION_1_2)

        val loginCursor = db.query("SELECT * FROM login_data WHERE title = 'Apple ID'")
        assertThat(loginCursor.moveToFirst()).isTrue()
        assertThat(loginCursor.getString(loginCursor.getColumnIndexOrThrow("userId"))).isEqualTo("user@apple.com")
        assertThat(loginCursor.getLong(loginCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(
            1000L
        )
        loginCursor.close()

        val cardCursor = db.query("SELECT * FROM bank_card_data WHERE title = 'Visa Card'")
        assertThat(cardCursor.moveToFirst()).isTrue()
        assertThat(cardCursor.getString(cardCursor.getColumnIndexOrThrow("number"))).isEqualTo("12345678")
        cardCursor.close()

        val accountCursor =
            db.query("SELECT * FROM bank_account_data WHERE title = 'Checking Account'")
        assertThat(accountCursor.moveToFirst()).isTrue()
        assertThat(accountCursor.getString(accountCursor.getColumnIndexOrThrow("accountNumber"))).isEqualTo(
            "9876543210"
        )
        accountCursor.close()

        db.close()
    }

    @Test
    fun migration_2_3_shouldSetCreationDateEqualToUpdateDateAcrossTables() {
        var db = helper.createDatabase(TEST_DB, 2)
        db.execSQL(
            "INSERT INTO login_data (`title`, `url`, `password`, `notes`, `userId`, `creationDate`, `updateDate`) " +
                    "VALUES ('Google ID', 'google.com', 'pass', 'notes', 'user@google.com', 1000, 5000)"
        )
        db.execSQL(
            "INSERT INTO bank_card_data (`title`, `name`, `number`, `pin`, `cvv`, `expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Mastercard', 'Jane Doe', '87654321', '4321', '888', '11/27', 'notes', 2000, 6000)"
        )
        db.execSQL(
            "INSERT INTO bank_account_data (`title`, `accountNumber`, `customerName`, `customerId`, `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Savings Account', '1122334455', 'Jane Doe', 'C-2', 'B-2', 'East', 'Addr', 'IFSC002', 'MICR002', 'notes', 3000, 7000)"
        )
        db.execSQL(
            "INSERT INTO secure_note_data (`title`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Secret Note', 'My confidential note', 4000, 8000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration.MIGRATION_2_3)

        val loginCursor =
            db.query("SELECT creationDate, updateDate FROM login_data WHERE title = 'Google ID'")
        assertThat(loginCursor.moveToFirst()).isTrue()
        assertThat(loginCursor.getLong(loginCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(
            5000L
        )
        loginCursor.close()

        val cardCursor =
            db.query("SELECT creationDate, updateDate FROM bank_card_data WHERE title = 'Mastercard'")
        assertThat(cardCursor.moveToFirst()).isTrue()
        assertThat(cardCursor.getLong(cardCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(
            6000L
        )
        cardCursor.close()

        val accountCursor =
            db.query("SELECT creationDate, updateDate FROM bank_account_data WHERE title = 'Savings Account'")
        assertThat(accountCursor.moveToFirst()).isTrue()
        assertThat(accountCursor.getLong(accountCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(
            7000L
        )
        accountCursor.close()

        val noteCursor =
            db.query("SELECT creationDate, updateDate FROM secure_note_data WHERE title = 'Secret Note'")
        assertThat(noteCursor.moveToFirst()).isTrue()
        assertThat(noteCursor.getLong(noteCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(
            8000L
        )
        noteCursor.close()

        db.close()
    }

    @Test
    fun migration_3_4_shouldCreateBackupMetadataTable() {
        var db = helper.createDatabase(TEST_DB, 3)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration.MIGRATION_3_4)

        db.execSQL(
            "INSERT INTO backup_metadata (`uriString`, `displayPath`, `lastBackupDate`, `createdOn`) " +
                    "VALUES ('content://backups', '/sdcard/backups', 123456789, 123456789)"
        )
        val cursor = db.query("SELECT * FROM backup_metadata WHERE uriString = 'content://backups'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("displayPath"))).isEqualTo("/sdcard/backups")
        cursor.close()

        db.close()
    }

    @Test
    fun migration_4_5_shouldCreateWritableAuthenticatorTable() {
        var db = helper.createDatabase(TEST_DB, 4)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, Migration.MIGRATION_4_5)

        db.execSQL(
            "INSERT INTO authenticator_data (`title`, `secretKey`, `algorithm`, `digits`, `period`, `creationDate`, `updateDate`) " +
                    "VALUES ('Google - alex@gmail.com', 'JBSWY3DPEHPK3PXP', 'SHA1', 6, 30, 1000, 2000)"
        )
        val cursor = db.query("SELECT * FROM authenticator_data WHERE title = 'Google - alex@gmail.com'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("secretKey"))).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("algorithm"))).isEqualTo("SHA1")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("digits"))).isEqualTo(6)
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("period"))).isEqualTo(30)
        cursor.close()

        db.close()
    }

    @Test
    fun migration_4_5_shouldPreserveExistingRecordsOfEveryOtherType() {
        var db = helper.createDatabase(TEST_DB, 4)
        db.execSQL(
            "INSERT INTO login_data (`title`, `url`, `password`, `notes`, `userId`, `creationDate`, `updateDate`) " +
                    "VALUES ('Netflix', 'netflix.com', 'pass', 'notes', 'user@netflix.com', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_card_data (`title`, `name`, `number`, `pin`, `cvv`, `expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Amex', 'John Doe', '378282246310005', '1234', '999', '01/30', 'notes', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_account_data (`title`, `accountNumber`, `customerName`, `customerId`, `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Salary Account', '5566778899', 'John Doe', 'C-3', 'B-3', 'West', 'Addr', 'IFSC003', 'MICR003', 'notes', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO secure_note_data (`title`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Recovery Codes', 'my confidential note', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO backup_metadata (`uriString`, `displayPath`, `lastBackupDate`, `createdOn`) " +
                    "VALUES ('content://backups', '/sdcard/backups', 1000, 2000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, Migration.MIGRATION_4_5)

        val loginCursor = db.query("SELECT * FROM login_data WHERE title = 'Netflix'")
        assertThat(loginCursor.moveToFirst()).isTrue()
        assertThat(loginCursor.getString(loginCursor.getColumnIndexOrThrow("userId"))).isEqualTo("user@netflix.com")
        loginCursor.close()

        val cardCursor = db.query("SELECT * FROM bank_card_data WHERE title = 'Amex'")
        assertThat(cardCursor.moveToFirst()).isTrue()
        assertThat(cardCursor.getString(cardCursor.getColumnIndexOrThrow("number"))).isEqualTo("378282246310005")
        cardCursor.close()

        val accountCursor = db.query("SELECT * FROM bank_account_data WHERE title = 'Salary Account'")
        assertThat(accountCursor.moveToFirst()).isTrue()
        assertThat(accountCursor.getString(accountCursor.getColumnIndexOrThrow("accountNumber"))).isEqualTo("5566778899")
        accountCursor.close()

        val noteCursor = db.query("SELECT * FROM secure_note_data WHERE title = 'Recovery Codes'")
        assertThat(noteCursor.moveToFirst()).isTrue()
        assertThat(noteCursor.getString(noteCursor.getColumnIndexOrThrow("notes"))).isEqualTo("my confidential note")
        noteCursor.close()

        val backupCursor = db.query("SELECT * FROM backup_metadata WHERE uriString = 'content://backups'")
        assertThat(backupCursor.moveToFirst()).isTrue()
        assertThat(backupCursor.getString(backupCursor.getColumnIndexOrThrow("displayPath"))).isEqualTo("/sdcard/backups")
        backupCursor.close()

        db.close()
    }
}