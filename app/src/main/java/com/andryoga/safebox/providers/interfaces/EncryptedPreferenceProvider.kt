package com.andryoga.safebox.providers.interfaces

interface EncryptedPreferenceProvider {
    fun upsertBooleanPref(key: String, value: Boolean)
    fun getBooleanPref(key: String, defValue: Boolean): Boolean

    fun upsertStringPref(key: String, value: String)
    fun getStringPref(key: String, defValue: String?): String?

    fun upsertIntPref(key: String, value: Int)
    fun getIntPref(key: String, defValue: Int): Int

    fun removePrefByKey(key: String)
}
