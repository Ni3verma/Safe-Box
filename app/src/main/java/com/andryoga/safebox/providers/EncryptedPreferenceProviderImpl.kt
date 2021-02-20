package com.andryoga.safebox.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider


class EncryptedPreferenceProviderImpl(context: Context) :
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

    override fun removePrefByKey(key: String) {
        sharedPref.edit().remove(key).apply()
    }

    private fun getMasterKey(): MasterKey {
        return MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
}