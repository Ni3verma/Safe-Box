package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import java.util.Date

@Dao
interface UserDetailsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity)

    @Query("select password from user_details limit 1")
    suspend fun getUserPassword(): String

    @Query("select hint from user_details")
    suspend fun getHint(): String?

    @Query("select uid from user_details ")
    suspend fun getUid(): String

    @Query("update user_details set password = :password, hint = :hint, updateDate = :updateDate")
    suspend fun updatePasswordAndHint(password: String, hint: String, updateDate: Date)
}
