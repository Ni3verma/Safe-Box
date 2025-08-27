package com.andryoga.composeapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.andryoga.composeapp.data.db.dao.BackupMetadataDao
import com.andryoga.composeapp.data.db.dao.BankAccountDataDao
import com.andryoga.composeapp.data.db.dao.BankCardDataDao
import com.andryoga.composeapp.data.db.dao.LoginDataDao
import com.andryoga.composeapp.data.db.dao.SecureNoteDataDao
import com.andryoga.composeapp.data.db.dao.UserDetailsDao
import com.andryoga.composeapp.data.db.entity.BackupMetadataEntity
import com.andryoga.composeapp.data.db.entity.BankAccountDataEntity
import com.andryoga.composeapp.data.db.entity.BankCardDataEntity
import com.andryoga.composeapp.data.db.entity.LoginDataEntity
import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import com.andryoga.composeapp.data.db.entity.UserDetailsEntity

@Database(
    entities = [
        BankAccountDataEntity::class,
        LoginDataEntity::class,
        UserDetailsEntity::class,
        BankCardDataEntity::class,
        SecureNoteDataEntity::class,
        BackupMetadataEntity::class
    ],
    version = 4
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase() {
    abstract fun bankAccountDataDao(): BankAccountDataDao
    abstract fun loginDataDao(): LoginDataDao
    abstract fun userDetailsDao(): UserDetailsDao
    abstract fun bankCardDataDao(): BankCardDataDao
    abstract fun secureNoteDataDao(): SecureNoteDataDao
    abstract fun backupMetadataDao(): BackupMetadataDao

    companion object {
        const val DATABASE_NAME: String = "SAFEBOX_APP_DB"
    }
}
