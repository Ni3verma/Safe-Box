package com.andryoga.safebox.data.repository.interfaces

interface UserDetailsRepository {
    // todo: make hint non nullable
    suspend fun insertUserDetailsData(password: String, hint: String?)
    suspend fun checkPassword(password: String): Boolean
    suspend fun onAuthSuccess(withBiometric: Boolean = false)
    suspend fun getHint(): String?
    suspend fun shouldStartBiometricAuthFlow(): Boolean
}
