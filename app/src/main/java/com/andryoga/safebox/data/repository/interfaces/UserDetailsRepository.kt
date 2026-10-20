package com.andryoga.safebox.data.repository.interfaces

interface UserDetailsRepository {
    suspend fun insertUserDetailsData(password: String, hint: String)
    suspend fun checkPassword(password: String): Boolean

    /**
     * Records a successful unlock: biometric allowance, Crashlytics user id, login counter.
     *
     * @param withBiometric whether the unlock was biometric rather than the master password
     * @return the 1-based ordinal of this login for this install, for analytics
     */
    suspend fun onAuthSuccess(withBiometric: Boolean = false): Int
    suspend fun getHint(): String?
    suspend fun shouldStartBiometricAuthFlow(): Boolean
    suspend fun updatePasswordAndHint(newPassword: String, hint: String)
}
