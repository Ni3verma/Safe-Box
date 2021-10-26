package com.andryoga.safebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andryoga.safebox.data.db.dao.*
import com.andryoga.safebox.data.db.entity.*
import timber.log.Timber

@Database(
    entities = [
        BankAccountDataEntity::class,
        LoginDataEntity::class,
        UserDetailsEntity::class,
        BankCardDataEntity::class,
        SecureNoteDataEntity::class
    ],
    version = 2
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase() {
    abstract fun bankAccountDataDao(): BankAccountDataDao
    abstract fun loginDataDao(): LoginDataDao
    abstract fun userDetailsDao(): UserDetailsDao
    abstract fun bankCardDataDao(): BankCardDataDao
    abstract fun secureNoteDataDao(): SecureNoteDataDao

    companion object {
        const val DATABASE_NAME: String = "SAFEBOX_APP_DB"
    }
}

// https://github.com/Ni3verma/Safe-Box/issues/88
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val migrationMessage = "migration from 1 to 2"
        Timber.i(migrationMessage)

        // bank account
        database.execSQL(
            "ALTER TABLE bank_account_data RENAME TO bank_account_data_tmp;"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `bank_account_data` " +
                "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`accountNumber` TEXT NOT NULL, `customerName` TEXT, `customerId` TEXT, " +
                "`branchCode` TEXT, `branchName` TEXT, `branchAddress` TEXT, " +
                "`ifscCode` TEXT, `micrCode` TEXT, `notes` TEXT, `creationDate` INTEGER NOT NULL, " +
                "`updateDate` INTEGER NOT NULL);"
        )
        database.execSQL(
            "INSERT INTO bank_account_data(`key`, `title`, `accountNumber`, `customerName`, `customerId`," +
                " `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, `notes`, " +
                "`creationDate`, `updateDate`) " +
                "SELECT `key`, `title`, `accountNumber`, `customerName`, `customerId`," +
                " `branchCode`, `branchName`, `branchAddress`, `ifscCode`, `micrCode`, " +
                "`notes`, `creationDate`, `updateDate` FROM bank_account_data_tmp;"
        )
        database.execSQL(
            "DROP TABLE bank_account_data_tmp;"
        )

        // login
        database.execSQL(
            "ALTER TABLE login_data RENAME TO login_data_tmp;"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `login_data` " +
                "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`url` TEXT, `password` TEXT, `notes` TEXT, `userId` TEXT NOT NULL, " +
                "`creationDate` INTEGER NOT NULL, `updateDate` INTEGER NOT NULL);"
        )
        database.execSQL(
            "INSERT INTO login_data(`key`, `title`, `url`, `password`, `notes`, `userId`," +
                "`creationDate`, `updateDate`) " +
                "SELECT `key`, `title`, `url`, `password`, `notes`, `userId`,`creationDate`," +
                " `updateDate` FROM login_data_tmp;"
        )
        database.execSQL(
            "DROP TABLE login_data_tmp;"
        )

        // bank card
        database.execSQL(
            "ALTER TABLE bank_card_data RENAME TO bank_card_data_tmp;"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `bank_card_data` " +
                "(`key` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`name` TEXT, `number` TEXT NOT NULL, `pin` TEXT, `cvv` TEXT, " +
                "`expiryDate` TEXT, `notes` TEXT, `creationDate` INTEGER NOT NULL," +
                " `updateDate` INTEGER NOT NULL);"
        )
        database.execSQL(
            "INSERT INTO bank_card_data(`key`, `title`, `name`, `number`, `pin`, `cvv`, " +
                "`expiryDate`, `notes`, `creationDate`, `updateDate`) " +
                "SELECT `key`, `title`, `name`, `number`, `pin`, `cvv`," +
                " `expiryDate`, `notes`, `creationDate`, `updateDate` FROM bank_card_data_tmp;"
        )
        database.execSQL(
            "DROP TABLE bank_card_data_tmp;"
        )

        Timber.i("$migrationMessage success")
    }
}
