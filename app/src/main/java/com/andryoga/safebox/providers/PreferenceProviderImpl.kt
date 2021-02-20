package com.andryoga.safebox.providers

import android.content.Context
import android.content.SharedPreferences
import com.andryoga.safebox.providers.interfaces.PreferenceProvider

class PreferenceProviderImpl(context: Context) :
    PreferenceProvider {
    private val appContext = context.applicationContext
    private val sharedPref: SharedPreferences = appContext.getSharedPreferences(
        "SAFE_BOX_SHARED_PREF",
        Context.MODE_PRIVATE
    )

    override fun upsertBooleanPref(key: String, value: Boolean) {
        sharedPref.edit().putBoolean(key, value).apply()
    }

    override fun getBooleanPref(key: String, defValue: Boolean): Boolean {
        return sharedPref.getBoolean(key, defValue)
    }

    override fun upsertStringPref(key: String, value: String) {
        sharedPref.edit().putString(key, value).apply()
    }

    override fun getStringPref(key: String, defValue: String?): String? {
        return sharedPref.getString(key, defValue)
    }

    override fun upsertLongPref(key: String, value: Long) {
        sharedPref.edit().putLong(key, value).apply()
    }

    override fun getLongPref(key: String, defValue: Long): Long {
        return sharedPref.getLong(key, defValue)
    }

    override fun removePrefByKey(key: String) {
        sharedPref.edit().remove(key).apply()
    }
}