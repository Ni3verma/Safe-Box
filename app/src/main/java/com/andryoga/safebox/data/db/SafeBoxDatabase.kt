package com.andryoga.safebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.UserDetailsEntity

@Database(
    entities = [LoginDataEntity::class, UserDetailsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SafeBoxDatabase : RoomDatabase() {
    abstract fun loginDataDao(): LoginDataDao
    abstract fun userDetailsDao(): UserDetailsDao

    companion object{
        const val DATABASE_NAME: String = "SAFEBOX_APP_DB"
    }
}