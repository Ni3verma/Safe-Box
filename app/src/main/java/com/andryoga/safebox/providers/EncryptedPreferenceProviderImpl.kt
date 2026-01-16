package com.andryoga.safebox.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EncryptedPreferenceProviderImpl @Inject constructor(
    @ApplicationContext context: Context
) :
    EncryptedPreferenceProvider {
    private val appContext = context.applicationContext
    private val sharedPref: SharedPreferences

    init {
        val masterKey = getMasterKey()
        sharedPref = EncryptedSharedPreferences.create(
            appContext,
            "encrypted_pref_safe_box",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun upsertBooleanPref(key: String, value: Boolean) {
        withContext(Dispatchers.IO)
        { sharedPref.edit { putBoolean(key, value) } }
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

    override suspend fun getIntPref(key: String, defValue: Int): Int {
        return withContext(Dispatchers.IO) { sharedPref.getInt(key, defValue) }
    }

    override suspend fun upsertIntPref(key: String, value: Int) {
        withContext(Dispatchers.IO) { sharedPref.edit { putInt(key, value) } }
    }

    override suspend fun removePrefByKey(key: String) {
        withContext(Dispatchers.IO) { sharedPref.edit { remove(key) } }
    }

    private fun getMasterKey(): MasterKey {
        return MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
}
