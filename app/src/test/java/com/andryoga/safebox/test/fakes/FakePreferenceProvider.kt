package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.providers.interfaces.PreferenceProvider

class FakePreferenceProvider : PreferenceProvider {
    private val preferences = mutableMapOf<String, Any>()

    override suspend fun upsertBooleanPref(key: String, value: Boolean) {
        preferences[key] = value
    }

    override suspend fun getBooleanPref(key: String, defValue: Boolean): Boolean {
        return (preferences[key] as? Boolean) ?: defValue
    }

    override suspend fun upsertStringPref(key: String, value: String) {
        preferences[key] = value
    }

    override suspend fun getStringPref(key: String, defValue: String?): String? {
        return (preferences[key] as? String) ?: defValue
    }

    override suspend fun upsertLongPref(key: String, value: Long) {
        preferences[key] = value
    }

    override suspend fun getLongPref(key: String, defValue: Long): Long {
        return (preferences[key] as? Long) ?: defValue
    }

    override suspend fun upsertIntPref(key: String, value: Int) {
        preferences[key] = value
    }

    override suspend fun getIntPref(key: String, defValue: Int): Int {
        return (preferences[key] as? Int) ?: defValue
    }

    override suspend fun removePrefByKey(key: String) {
        preferences.remove(key)
    }

    fun clearAll() {
        preferences.clear()
    }
}
