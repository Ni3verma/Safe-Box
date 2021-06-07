package com.andryoga.safebox.providers.interfaces

interface PreferenceProvider {
    fun upsertBooleanPref(key: String, value: Boolean)
    fun getBooleanPref(key: String, defValue: Boolean): Boolean

    fun upsertStringPref(key: String, value: String)
    fun getStringPref(key: String, defValue: String?): String?

    fun upsertLongPref(key: String, value: Long)
    fun getLongPref(key: String, defValue: Long): Long

    fun removePrefByKey(key: String)
}
