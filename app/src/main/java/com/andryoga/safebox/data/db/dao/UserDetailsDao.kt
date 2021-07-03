package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.UserDetailsEntity

@Dao
interface UserDetailsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity)

    @Query("select * from user_details limit 1")
    suspend fun getUserDetails(): UserDetailsEntity

    @Query("select hint from user_details")
    suspend fun getHint(): UserDetailsEntity
}
