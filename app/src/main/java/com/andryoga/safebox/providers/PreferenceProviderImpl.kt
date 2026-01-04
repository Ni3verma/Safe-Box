package com.andryoga.safebox.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PreferenceProviderImpl @Inject constructor(
    @ApplicationContext context: Context
) :
    PreferenceProvider {
    private val appContext = context.applicationContext
    private val sharedPref: SharedPreferences = appContext.getSharedPreferences(
        "SAFE_BOX_SHARED_PREF",
        Context.MODE_PRIVATE
    )

    override suspend fun upsertBooleanPref(key: String, value: Boolean) {
        withContext(Dispatchers.IO) { sharedPref.edit { putBoolean(key, value) } }
    }

    override suspend fun getBooleanPref(key: String, defValue: Boolean): Boolean {
        return withContext(Dispatchers.IO) { sharedPref.getBoolean(key, defValue) }
    }

    override suspend fun upsertStringPref(key: String, value: String) {
        withContext(Dispatchers.IO) { sharedPref.edit { putString(key, value) } }
    }

    override suspend fun getStringPref(key: String, defValue: String?): String? {
        return withContext(Dispatchers.IO) { sharedPref.getString(key, defValue) }
    }

    override suspend fun upsertLongPref(key: String, value: Long) {
        withContext(Dispatchers.IO) { sharedPref.edit { putLong(key, value) } }
    }

    override suspend fun getLongPref(key: String, defValue: Long): Long {
        return withContext(Dispatchers.IO) { sharedPref.getLong(key, defValue) }
    }

    override suspend fun getIntPref(key: String, defValue: Int): Int {
        return withContext(Dispatchers.IO) { sharedPref.getInt(key, defValue) }
    }

    override suspend fun upsertIntPref(key: String, value: Int) {
        withContext(Dispatchers.IO) { sharedPref.edit { putInt(key, value) } }
    }

    override suspend fun removePrefByKey(key: String) {
        withContext(Dispatchers.IO) { sharedPref.edit { remove(key) } }
    }
}
