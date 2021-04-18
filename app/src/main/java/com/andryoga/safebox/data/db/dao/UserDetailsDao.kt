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

    /*
    * This function validates whether password entered is correct or not
    * @author Nitin
    * @param password This is the password that needs to be checked
    * @return int If output is 1 then entered password is correct, otherwise wrong
    * */
    @Query("select count(*) from user_details where password=:password limit 1")
    suspend fun checkPassword(password: String): Int
}