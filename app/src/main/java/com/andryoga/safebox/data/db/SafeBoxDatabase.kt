package com.andryoga.safebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.UserDetailsEntity

@Database(
    entities = [BankAccountDataEntity::class, LoginDataEntity::class, UserDetailsEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase() {
    abstract fun bankAccountDataDao(): BankAccountDataDao
    abstract fun loginDataDao(): LoginDataDao
    abstract fun userDetailsDao(): UserDetailsDao
    companion object {
        const val DATABASE_NAME: String = "SAFEBOX_APP_DB"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "create table `bank_account_data` (`key` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`accountNumber` TEXT NOT NULL, `customerName` TEXT,`customerId` TEXT NOT NULL," +
                        "`branchCode` TEXT, `branchName` TEXT, `branchAddress` TEXT," +
                        "`ifscCode` TEXT NOT NULL, `micrCode` TEXT, `notes` TEXT, PRIMARY KEY(`key`))"
                )
            }
        }
    }
}
