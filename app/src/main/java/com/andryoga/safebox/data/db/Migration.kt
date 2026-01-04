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

    // no schema change here, just sanitizing data for v2 (compose app)
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            UPDATE bank_card_data 
            SET expiryDate = REPLACE(TRIM(expiryDate), '/', '') 
            WHERE expiryDate LIKE '%/%'
            """.trimIndent()
            )
        }
    }
}
