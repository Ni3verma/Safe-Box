package com.andryoga.safebox.providers.interfaces

interface PreferenceProvider {
    suspend fun upsertBooleanPref(key: String, value: Boolean)
    suspend fun getBooleanPref(key: String, defValue: Boolean): Boolean

    suspend fun upsertStringPref(key: String, value: String)
    suspend fun getStringPref(key: String, defValue: String?): String?

    suspend fun upsertLongPref(key: String, value: Long)
    suspend fun getLongPref(key: String, defValue: Long): Long

    suspend fun upsertIntPref(key: String, value: Int)
    suspend fun getIntPref(key: String, defValue: Int): Int

    suspend fun removePrefByKey(key: String)
}
