package com.andryoga.composeapp.data.repository.interfaces

interface UserDetailsRepository {
    suspend fun insertUserDetailsData(password: String, hint: String?)
    suspend fun checkPassword(password: String): Boolean
    suspend fun getHint(): String?
}
