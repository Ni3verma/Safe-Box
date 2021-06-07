package com.andryoga.safebox.data.repository.interfaces

interface UserDetailsRepository {
    suspend fun insertUserDetailsData(password: String, hint: String)
    suspend fun checkPassword(password: String): Boolean
}
