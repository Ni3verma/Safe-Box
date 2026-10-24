package com.andryoga.safebox.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.Date

class MigrationTest {
    companion object {
        private const val TEST_DB = "migration-test"

        /** Oldest schema an installed app can still be sitting on. */
        private const val FIRST_VERSION = 1

        /** Throwaway database used only to read the current schema version off Room. */
        private const val VERSION_PROBE_DB = "schema-version-probe"

        /** A hash in the `b64(hash)|b64(salt)` shape `HashingUtilsImpl` writes; the bytes are arbitrary. */
        private const val LEGACY_HASH = "aGFzaA==|c2FsdA=="
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

    @Test
    fun migration_5_6_shouldParkCredentialStateInVaultMetaAndDropPasswordColumn() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            "INSERT INTO user_details (`uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-legacy', '$LEGACY_HASH', 'ENC[hint]', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO login_data (`title`, `url`, `password`, `notes`, `userId`, `creationDate`, `updateDate`) " +
                    "VALUES ('Netflix', 'netflix.com', 'pass', 'notes', 'user@netflix.com', 1000, 2000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration.MIGRATION_5_6)

        assertThat(vaultMetaValue(db, VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.LEGACY)
        assertThat(vaultMetaValue(db, VaultMetaKey.LEGACY_PASSWORD_HASH)).isEqualTo(LEGACY_HASH)
        assertThat(columnNames(db, "user_details")).doesNotContain("password")

        val userCursor = db.query("SELECT * FROM user_details")
        assertThat(userCursor.count).isEqualTo(1)
        assertThat(userCursor.moveToFirst()).isTrue()
        assertThat(userCursor.getInt(userCursor.getColumnIndexOrThrow("key"))).isEqualTo(1)
        assertThat(userCursor.getString(userCursor.getColumnIndexOrThrow("uid"))).isEqualTo("uid-legacy")
        assertThat(userCursor.getString(userCursor.getColumnIndexOrThrow("hint"))).isEqualTo("ENC[hint]")
        assertThat(userCursor.getLong(userCursor.getColumnIndexOrThrow("creationDate"))).isEqualTo(1000L)
        assertThat(userCursor.getLong(userCursor.getColumnIndexOrThrow("updateDate"))).isEqualTo(2000L)
        userCursor.close()

        val loginCursor = db.query("SELECT * FROM login_data WHERE title = 'Netflix'")
        assertThat(loginCursor.moveToFirst()).isTrue()
        assertThat(loginCursor.getString(loginCursor.getColumnIndexOrThrow("userId"))).isEqualTo("user@netflix.com")
        loginCursor.close()

        db.close()
    }

    @Test
    fun migration_5_6_withoutUser_shouldCreateTablesButNoCryptoVersionRow() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration.MIGRATION_5_6)

        assertThat(rowCount(db, "vault_meta")).isEqualTo(0)
        assertThat(rowCount(db, "user_details")).isEqualTo(0)
        assertThat(columnNames(db, "user_details"))
            .containsExactly("key", "uid", "hint", "creationDate", "updateDate")

        db.close()
    }

    @Test
    fun migration_5_6_shouldKeepUserDetailsAutoincrementAboveCopiedKeys() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            "INSERT INTO user_details (`key`, `uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES (5, 'uid-five', '$LEGACY_HASH', NULL, 1000, 2000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration.MIGRATION_5_6)

        // The rebuilt table must not hand out a key that the copied row already holds.
        db.execSQL(
            "INSERT INTO user_details (`uid`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-next', NULL, 3000, 3000)"
        )
        val cursor = db.query("SELECT `key` FROM user_details WHERE uid = 'uid-next'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(0)).isGreaterThan(5)
        cursor.close()

        db.close()
    }

    @Test
    fun migration_5_6_withTwoUserRows_shouldParkTheFirstRowsHashAndCopyBothRows() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            "INSERT INTO user_details (`uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-first', '$LEGACY_HASH', 'ENC[first]', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO user_details (`uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-second', 'other==|salt==', 'ENC[second]', 3000, 4000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration.MIGRATION_5_6)

        // The old getUserPassword() read `LIMIT 1` in rowid order; the parked hash is that row's.
        assertThat(vaultMetaValue(db, VaultMetaKey.LEGACY_PASSWORD_HASH)).isEqualTo(LEGACY_HASH)
        assertThat(rowCount(db, "vault_meta")).isEqualTo(2)
        assertThat(rowCount(db, "user_details")).isEqualTo(2)

        db.close()
    }

    /**
     * The upgraded-user case through the production DAOs rather than raw SQL: Room runs the
     * migration itself on open, validates the result against the entities, and the queries the
     * login screen uses must serve the parked credential and the untouched hint.
     */
    @Test
    fun migration_5_6_thenOpeningWithRoom_shouldServeParkedCredentialAndHintThroughDaos() {
        val legacyDb = helper.createDatabase(TEST_DB, 5)
        legacyDb.execSQL(
            "INSERT INTO user_details (`uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-legacy', '$LEGACY_HASH', 'ENC[hint]', 1000, 2000)"
        )
        legacyDb.close()

        val database = openWithRoom()
        try {
            runBlocking {
                assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION))
                    .isEqualTo(CryptoVersion.LEGACY)
                assertThat(database.vaultMetaDao().get(VaultMetaKey.LEGACY_PASSWORD_HASH))
                    .isEqualTo(LEGACY_HASH)
                assertThat(database.userDetailsDao().getUid()).isEqualTo("uid-legacy")
                assertThat(database.userDetailsDao().getHint()).isEqualTo("ENC[hint]")

                database.userDetailsDao().updateHint("ENC[new hint]", Date(5000))
                assertThat(database.userDetailsDao().getHint()).isEqualTo("ENC[new hint]")
            }
        } finally {
            database.close()
        }
    }

    /**
     * The never-signed-up case: an installed app with an empty database upgrades, nothing is
     * parked, and the signup writes that follow succeed on the rebuilt table.
     */
    @Test
    fun migration_5_6_withoutUser_thenOpeningWithRoom_shouldAcceptSignupWrites() {
        helper.createDatabase(TEST_DB, 5).close()

        val database = openWithRoom()
        try {
            runBlocking {
                assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION)).isNull()
                assertThat(database.vaultMetaDao().get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isNull()
                assertThat(database.userDetailsDao().getHint()).isNull()

                database.vaultMetaDao()
                    .upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
                database.vaultMetaDao()
                    .upsert(VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, LEGACY_HASH))
                database.userDetailsDao().insertUserDetailsData(
                    UserDetailsEntity(
                        key = 0,
                        uid = "uid-new",
                        hint = "ENC[new]",
                        creationDate = Date(1000),
                        updateDate = Date(1000),
                    ),
                )

                assertThat(database.vaultMetaDao().get(VaultMetaKey.LEGACY_PASSWORD_HASH))
                    .isEqualTo(LEGACY_HASH)
                assertThat(database.userDetailsDao().getUid()).isEqualTo("uid-new")
                assertThat(database.userDetailsDao().getHint()).isEqualTo("ENC[new]")
            }
        } finally {
            database.close()
        }
    }

    /**
     * Upgrades a version 1 database straight to whatever the current schema version is, in a
     * single [MigrationTestHelper.runMigrationsAndValidate] call.
     *
     * Every other test here covers one adjacent pair. None of them prove that a user who has not
     * opened the app in two years can cross all the versions at once, which is the upgrade that
     * actually happens in the wild and the one that has the most room to go wrong.
     *
     * Nothing is hardcoded to a specific version on purpose. The target comes from the real
     * database via [currentSchemaVersion] and the migrations come from [Migration.ALL], so this
     * test keeps testing the full chain as new versions are added, with no edit required here.
     */
    @Test
    fun migration_fromFirstVersionToCurrent_shouldUpgradeInOneChainWithoutDataLoss() {
        val targetVersion = currentSchemaVersion()
        val highestMigrationVersion = Migration.ALL.maxOf { it.endVersion }
        assertWithMessage(
            "The database is at version $targetVersion but the newest migration only reaches " +
                    "$highestMigrationVersion. Bumping the version without registering a matching " +
                    "migration in Migration.ALL makes Room throw on first launch for every user who " +
                    "already has the app installed."
        )
            .that(highestMigrationVersion)
            .isEqualTo(targetVersion)

        var db = helper.createDatabase(TEST_DB, FIRST_VERSION)
        // creationDate is deliberately different from updateDate so the assertions below can
        // prove MIGRATION_2_3 ran as part of the chain, not just the first and last migrations.
        db.execSQL(
            "INSERT INTO login_data (`title`, `url`, `password`, `notes`, `userId`, `creationDate`, `updateDate`) " +
                    "VALUES ('Legacy Login', 'legacy.com', 'secret', 'note', 'user@legacy.com', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_card_data (`title`, `name`, `number`, `pin`, `cvv`, `expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Legacy Card', 'John Doe', '4111111111111111', '1234', '999', '01/30', 'note', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO bank_account_data (`title`, `accountNumber`, `customerName`, `customerId`, `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, `creationDate`, `updateDate`) " +
                    "VALUES ('Legacy Account', '1122334455', 'John Doe', 'C-9', 'B-9', 'Main', 'Address', 'IFSC009', 'MICR009', 'note', 1000, 2000)"
        )
        db.execSQL(
            "INSERT INTO user_details (`uid`, `password`, `hint`, `creationDate`, `updateDate`) " +
                    "VALUES ('uid-chain', '$LEGACY_HASH', 'ENC[chain hint]', 1000, 2000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, targetVersion, true, *Migration.ALL)

        val loginCursor = db.query("SELECT * FROM login_data WHERE title = 'Legacy Login'")
        assertThat(loginCursor.moveToFirst()).isTrue()
        assertThat(loginCursor.getString(loginCursor.getColumnIndexOrThrow("userId")))
            .isEqualTo("user@legacy.com")
        // MIGRATION_2_3 rewrites creationDate to match updateDate, so 1000 becoming 2000 is the
        // evidence that the intermediate migration executed rather than being skipped over.
        assertWithMessage("MIGRATION_2_3 did not run as part of the full chain")
            .that(loginCursor.getLong(loginCursor.getColumnIndexOrThrow("creationDate")))
            .isEqualTo(2000L)
        loginCursor.close()

        val cardCursor = db.query("SELECT * FROM bank_card_data WHERE title = 'Legacy Card'")
        assertThat(cardCursor.moveToFirst()).isTrue()
        assertThat(cardCursor.getString(cardCursor.getColumnIndexOrThrow("number")))
            .isEqualTo("4111111111111111")
        cardCursor.close()

        val accountCursor =
            db.query("SELECT * FROM bank_account_data WHERE title = 'Legacy Account'")
        assertThat(accountCursor.moveToFirst()).isTrue()
        assertThat(accountCursor.getString(accountCursor.getColumnIndexOrThrow("accountNumber")))
            .isEqualTo("1122334455")
        accountCursor.close()

        // MIGRATION_5_6 moves the credential out of user_details; the hint stays where it was.
        assertWithMessage("MIGRATION_5_6 did not park the legacy hash as part of the full chain")
            .that(vaultMetaValue(db, VaultMetaKey.LEGACY_PASSWORD_HASH))
            .isEqualTo(LEGACY_HASH)
        assertThat(vaultMetaValue(db, VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.LEGACY)
        assertThat(columnNames(db, "user_details")).doesNotContain("password")
        val userCursor = db.query("SELECT * FROM user_details WHERE uid = 'uid-chain'")
        assertThat(userCursor.moveToFirst()).isTrue()
        assertThat(userCursor.getString(userCursor.getColumnIndexOrThrow("hint")))
            .isEqualTo("ENC[chain hint]")
        userCursor.close()

        // Tables introduced part way through the chain must exist and be writable at the end of it.
        db.execSQL(
            "INSERT INTO authenticator_data (`title`, `secretKey`, `algorithm`, `digits`, `period`, `creationDate`, `updateDate`) " +
                    "VALUES ('Legacy 2FA', 'ENCRYPTED_SEED', 'SHA1', 6, 30, 3000, 3000)"
        )
        val authenticatorCursor =
            db.query("SELECT * FROM authenticator_data WHERE title = 'Legacy 2FA'")
        assertThat(authenticatorCursor.moveToFirst()).isTrue()
        authenticatorCursor.close()

        db.execSQL(
            "INSERT INTO backup_metadata (`uriString`, `displayPath`, `lastBackupDate`, `createdOn`) " +
                    "VALUES ('content://legacy', '/sdcard/legacy', 4000, 4000)"
        )
        val backupCursor =
            db.query("SELECT * FROM backup_metadata WHERE uriString = 'content://legacy'")
        assertThat(backupCursor.moveToFirst()).isTrue()
        backupCursor.close()

        db.close()
    }

    /**
     * Reads the schema version the app currently ships, straight from the database Room builds.
     *
     * Room's `@Database` annotation is retained only at class level, so the version cannot be read
     * reflectively at runtime. Building a throwaway database and reading `PRAGMA user_version` off
     * it is the reliable way to learn the real number without duplicating it in the test.
     *
     * @return The version stamped on a freshly created [SafeBoxDatabase].
     */
    private fun currentSchemaVersion(): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(VERSION_PROBE_DB)
        val probe =
            Room.databaseBuilder(context, SafeBoxDatabase::class.java, VERSION_PROBE_DB).build()
        val version = probe.openHelper.readableDatabase.version
        probe.close()
        context.deleteDatabase(VERSION_PROBE_DB)
        return version
    }

    /**
     * Opens the database [helper] created under [TEST_DB] through Room itself, with the production
     * migration list, so `MIGRATION_5_6` runs the way it does on a device and Room validates the
     * outcome against the entities before any DAO call. The caller closes the returned database.
     */
    private fun openWithRoom(): SafeBoxDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.databaseBuilder(context, SafeBoxDatabase::class.java, TEST_DB)
            .addMigrations(*Migration.ALL)
            .build()
    }

    /** @return the `vault_meta` value stored under [key], or `null` when the row is absent. */
    private fun vaultMetaValue(db: SupportSQLiteDatabase, key: String): String? {
        val cursor = db.query("SELECT `value` FROM vault_meta WHERE `key` = ?", arrayOf(key))
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /** @return the column names of [table] in declaration order, from `PRAGMA table_info`. */
    private fun columnNames(db: SupportSQLiteDatabase, table: String): List<String> {
        val cursor = db.query("PRAGMA table_info(`$table`)")
        return cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            generateSequence { if (it.moveToNext()) it.getString(nameIndex) else null }.toList()
        }
    }

    private fun rowCount(db: SupportSQLiteDatabase, table: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM `$table`")
        return cursor.use { it.moveToFirst(); it.getInt(0) }
    }
}