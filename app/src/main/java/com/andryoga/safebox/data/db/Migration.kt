package com.andryoga.safebox.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

object Migration {
    // https://github.com/Ni3verma/Safe-Box/issues/88
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationMessage = "migration from 1 to 2"
            Timber.i(migrationMessage)

            // bank account
            db.execSQL(
                "ALTER TABLE bank_account_data RENAME TO bank_account_data_tmp;"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bank_account_data` " +
                        "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`accountNumber` TEXT NOT NULL, `customerName` TEXT, `customerId` TEXT, " +
                        "`branchCode` TEXT, `branchName` TEXT, `branchAddress` TEXT, " +
                        "`ifscCode` TEXT, `micrCode` TEXT, `notes` TEXT, `creationDate` INTEGER NOT NULL, " +
                        "`updateDate` INTEGER NOT NULL);"
            )
            db.execSQL(
                "INSERT INTO bank_account_data(`key`, `title`, `accountNumber`, `customerName`, `customerId`," +
                        " `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, " +
                        "`creationDate`, `updateDate`) " +
                        "SELECT `key`, `title`, `accountNumber`, `customerName`, `customerId`," +
                        " `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, " +
                        "`notes`, `creationDate`, `updateDate` FROM bank_account_data_tmp;"
            )
            db.execSQL(
                "DROP TABLE bank_account_data_tmp;"
            )

            // login
            db.execSQL(
                "ALTER TABLE login_data RENAME TO login_data_tmp;"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `login_data` " +
                        "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT, `password` TEXT, `notes` TEXT, `userId` TEXT NOT NULL, " +
                        "`creationDate` INTEGER NOT NULL, `updateDate` INTEGER NOT NULL);"
            )
            db.execSQL(
                "INSERT INTO login_data(`key`, `title`, `url`, `password`, `notes`, `userId`," +
                        "`creationDate`, `updateDate`) " +
                        "SELECT `key`, `title`, `url`, `password`, `notes`, `userId`,`creationDate`," +
                        " `updateDate` FROM login_data_tmp;"
            )
            db.execSQL(
                "DROP TABLE login_data_tmp;"
            )

            // bank card
            db.execSQL(
                "ALTER TABLE bank_card_data RENAME TO bank_card_data_tmp;"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bank_card_data` " +
                        "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`name` TEXT, `number` TEXT NOT NULL, `pin` TEXT, `cvv` TEXT, " +
                        "`expiryDate` TEXT, `notes` TEXT, `creationDate` INTEGER NOT NULL," +
                        " `updateDate` INTEGER NOT NULL);"
            )
            db.execSQL(
                "INSERT INTO bank_card_data(`key`, `title`, `name`, `number`, `pin`, `cvv`, " +
                        "`expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                        "SELECT `key`, `title`, `name`, `number`, `pin`, `cvv`," +
                        " `expiryDate`, `notes`, `creationDate`, `updateDate` FROM bank_card_data_tmp;"
            )
            db.execSQL(
                "DROP TABLE bank_card_data_tmp;"
            )

            Timber.i("$migrationMessage success")
        }
    }

    // https://github.com/Ni3verma/Safe-Box/issues/100
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationMessage = "migration from 2 to 3"
            Timber.i(migrationMessage)

            db.execSQL("update `bank_account_data` set `creationDate`=`updateDate`")
            db.execSQL("update `bank_card_data` set `creationDate`=`updateDate`")
            db.execSQL("update `login_data` set `creationDate`=`updateDate`")
            db.execSQL("update `secure_note_data` set `creationDate`=`updateDate`")

            Timber.i("$migrationMessage success")
        }
    }

    // https://github.com/Ni3verma/Safe-Box/issues/70
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationMessage = "migration from 3 to 4"
            Timber.i(migrationMessage)

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `backup_metadata` " +
                        "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uriString` TEXT NOT NULL, `displayPath` TEXT NOT NULL, " +
                        "`lastBackupDate` INTEGER, `createdOn` INTEGER NOT NULL)"
            )

            Timber.i("$migrationMessage success")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationMessage = "migration from 4 to 5"
            Timber.i(migrationMessage)

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `authenticator_data` (" +
                        "`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`secretKey` TEXT NOT NULL, " +
                        "`algorithm` TEXT NOT NULL, " +
                        "`digits` INTEGER NOT NULL, " +
                        "`period` INTEGER NOT NULL, " +
                        "`creationDate` INTEGER NOT NULL, " +
                        "`updateDate` INTEGER NOT NULL)"
            )

            Timber.i("$migrationMessage success")
        }
    }

    /**
     * Schema v6 (envelope encryption, design §4.2): `vault_meta`, and `user_details`
     * without the `password` column.
     *
     * The legacy credential state is parked in `vault_meta` first - `crypto_version = 1` for any
     * database that has a user, plus the row the old `getUserPassword()` read - so nothing the
     * login screen needs is lost when the column goes. The column is dropped by
     * create-copy-drop-rename because `DROP COLUMN` needs SQLite 3.35 and API 29-33 ship
     * 3.28-3.32. The `CREATE TABLE` statements are copied from `app/schemas/.../6.json`; the key
     * names and values are SQL literals on purpose: they are a wire format and must not follow a
     * Kotlin rename (`VaultMetaKey` carries the same strings for readers).
     *
     * Every destructive step is guarded by a row-count check. A failed check throws, Room rolls
     * the transaction back and the database stays at version 5 with the vault untouched - the
     * only acceptable outcome for a credential that has no other copy.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationMessage = "migration from 5 to 6"
            Timber.i(migrationMessage)

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vault_meta` " +
                        "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
            )

            val userRows = db.countRows("SELECT COUNT(*) FROM `user_details`")
            db.execSQL(
                "INSERT INTO `vault_meta` (`key`, `value`) " +
                        "SELECT 'crypto_version', '1' WHERE EXISTS (SELECT 1 FROM `user_details`)"
            )
            db.execSQL(
                "INSERT INTO `vault_meta` (`key`, `value`) " +
                        "SELECT 'legacy_password_hash', `password` FROM `user_details` LIMIT 1"
            )
            // Both rows for a vault with a user, none for a database that never signed up.
            val expectedMetaRows = if (userRows > 0) 2L else 0L
            val metaRows = db.countRows("SELECT COUNT(*) FROM `vault_meta`")
            check(metaRows == expectedMetaRows) {
                "$migrationMessage: vault_meta has $metaRows rows, expected $expectedMetaRows for $userRows user_details rows"
            }
            Timber.i("$migrationMessage: credential state parked, user_details rows=$userRows")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_details_new` " +
                        "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                        "`hint` TEXT, `creationDate` INTEGER NOT NULL, `updateDate` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `user_details_new` (`key`, `uid`, `hint`, `creationDate`, `updateDate`) " +
                        "SELECT `key`, `uid`, `hint`, `creationDate`, `updateDate` FROM `user_details`"
            )
            val copiedRows = db.countRows("SELECT COUNT(*) FROM `user_details_new`")
            check(copiedRows == userRows) {
                "$migrationMessage: user_details copy has $copiedRows rows, expected $userRows"
            }
            db.execSQL("DROP TABLE `user_details`")
            db.execSQL("ALTER TABLE `user_details_new` RENAME TO `user_details`")

            Timber.i("$migrationMessage success")
        }
    }

    private fun SupportSQLiteDatabase.countRows(countQuery: String): Long {
        return query(countQuery).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }

    /**
     * Every migration this app knows about, ordered oldest first.
     *
     * Single source of truth deliberately: the database builder and the migration tests both read
     * this array, so adding a migration here is enough to wire it into production and to pull it
     * into the full-chain upgrade test. Keeping two hand-maintained lists in sync is exactly how a
     * migration ends up shipped but unregistered.
     */
    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
