package com.andryoga.composeapp.providers.interfaces

/**
 * This provider interacts with encrypted preferences. It is safe to call the methods from main thread.
 * */
interface EncryptedPreferenceProvider {
    suspend fun upsertBooleanPref(key: String, value: Boolean)
    suspend fun getBooleanPref(key: String, defValue: Boolean): Boolean

    suspend fun upsertStringPref(key: String, value: String)
    suspend fun getStringPref(key: String, defValue: String?): String?

    suspend fun upsertIntPref(key: String, value: Int)
    suspend fun getIntPref(key: String, defValue: Int): Int

    suspend fun removePrefByKey(key: String)
}
