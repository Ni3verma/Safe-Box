package com.andryoga.safebox.data.dataStore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT
import com.andryoga.safebox.data.dataStore.SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT
import com.andryoga.safebox.data.dataStore.SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
import com.andryoga.safebox.data.dataStore.SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings_data_store")

data class Settings(
    val isPrivacyEnabled: Boolean = PRIVACY_ENABLED_DEFAULT,
    val autoBackupAfterPasswordLogin: Boolean = AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT,
    val awayTimeoutSec: Int = AWAY_TIMEOUT_DEFAULT,
    val passwordAfterXBiometricLogins: Int = PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT,
)

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferenceProvider: Lazy<PreferenceProvider>,
) {

    private object Keys {
        val PRIVACY_ENABLED = booleanPreferencesKey("privacy_enabled")
        val AUTO_BACKUP_AFTER_PASSWORD_LOGIN =
            booleanPreferencesKey("auto_backup_after_password_login")
        val AWAY_TIMEOUT = intPreferencesKey("away_timeout")
        val PASSWORD_AFTER_X_BIOMETRIC_LOGIN = intPreferencesKey("password_after_x_biometric_login")
    }

    object DefaultValues {
        const val PRIVACY_ENABLED_DEFAULT = true
        const val AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT = true
        const val AWAY_TIMEOUT_DEFAULT = 10
        const val PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT = 5
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            Settings(
                isPrivacyEnabled = prefs[Keys.PRIVACY_ENABLED] ?: PRIVACY_ENABLED_DEFAULT,
                autoBackupAfterPasswordLogin = prefs[Keys.AUTO_BACKUP_AFTER_PASSWORD_LOGIN]
                    ?: AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT,
                awayTimeoutSec = prefs[Keys.AWAY_TIMEOUT] ?: AWAY_TIMEOUT_DEFAULT,
                passwordAfterXBiometricLogins = prefs[Keys.PASSWORD_AFTER_X_BIOMETRIC_LOGIN]
                    ?: PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
            )
        }

    val isPrivacyEnabled = getSetting(Keys.PRIVACY_ENABLED, PRIVACY_ENABLED_DEFAULT)
    val awayTimeoutSec = getSetting(Keys.AWAY_TIMEOUT, AWAY_TIMEOUT_DEFAULT)
    val autoBackupAfterPasswordLogin =
        getSetting(Keys.AUTO_BACKUP_AFTER_PASSWORD_LOGIN, AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
    val passwordAfterXBiometricLogins =
        getSetting(Keys.PASSWORD_AFTER_X_BIOMETRIC_LOGIN, PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT)


    suspend fun updatePrivacy(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_ENABLED] = enabled }
    }

    suspend fun updateAwayTimeout(value: Int) {
        context.dataStore.edit { it[Keys.AWAY_TIMEOUT] = value }
    }

    suspend fun updateAutoBackupAfterPasswordLogin(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_AFTER_PASSWORD_LOGIN] = enabled }
    }

    suspend fun updatePasswordAfterXBiometricLogin(value: Int) {
        preferenceProvider.get()
            .upsertIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, value)
        context.dataStore.edit { it[Keys.PASSWORD_AFTER_X_BIOMETRIC_LOGIN] = value }
    }

    private fun <T> getSetting(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return context.dataStore.data
            .map { prefs -> prefs[key] ?: defaultValue }
            .distinctUntilChanged()
    }
}