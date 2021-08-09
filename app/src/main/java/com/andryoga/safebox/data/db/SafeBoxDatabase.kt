package com.andryoga.safebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.andryoga.safebox.data.db.dao.*
import com.andryoga.safebox.data.db.entity.*

@Database(
    entities = [
        BankAccountDataEntity::class,
        LoginDataEntity::class,
        UserDetailsEntity::class,
        BankCardDataEntity::class,
        SecureNoteDataEntity::class
    ],
    version = 1,
    exportSchema = false
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
