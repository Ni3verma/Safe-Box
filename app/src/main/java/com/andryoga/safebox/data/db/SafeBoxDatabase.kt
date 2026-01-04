package com.andryoga.safebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.entity.UserDetailsEntity

@Database(
    entities = [
        BankAccountDataEntity::class,
        LoginDataEntity::class,
        UserDetailsEntity::class,
        BankCardDataEntity::class,
        SecureNoteDataEntity::class,
        BackupMetadataEntity::class
    ],
    version = 5
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
