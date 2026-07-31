@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.dataStore

import android.content.Context
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsDataStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var fakePreferenceProvider: FakePreferenceProvider
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() = runTest {
        mockContext = mockk(relaxed = true)
        val dataDir = tempFolder.newFolder()
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns dataDir

        fakePreferenceProvider = FakePreferenceProvider()
        val lazyPreferenceProvider: Lazy<PreferenceProvider> = Lazy { fakePreferenceProvider }

        settingsDataStore = SettingsDataStore(
            context = mockContext,
            preferenceProvider = lazyPreferenceProvider
        )

        settingsDataStore.updatePrivacy(SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT)
        settingsDataStore.updateAwayTimeout(SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT)
        settingsDataStore.updateAutoBackupAfterPasswordLogin(SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
    }

    @Test
    fun settingsFlow_initially_shouldEmitDefaultSettingsValues() = runTest {
        settingsDataStore.settingsFlow.test {
            val settings = awaitItem()
            assertThat(settings.isPrivacyEnabled).isEqualTo(SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT)
            assertThat(settings.autoBackupAfterPasswordLogin).isEqualTo(SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
            assertThat(settings.awayTimeoutSec).isEqualTo(SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT)
            assertThat(settings.passwordAfterXBiometricLogins).isEqualTo(SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isPrivacyEnabledFlow_and_awayTimeoutSecFlow_initially_shouldEmitDefaults() = runTest {
        settingsDataStore.isPrivacyEnabledFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        settingsDataStore.awayTimeoutSecFlow.test {
            assertThat(awaitItem()).isEqualTo(10)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updatePrivacy_shouldUpdateDataStoreAndEmitNewValue() = runTest {
        settingsDataStore.updatePrivacy(false)

        assertThat(settingsDataStore.isPrivacyEnabledFlow.first()).isFalse()
        assertThat(settingsDataStore.settingsFlow.first().isPrivacyEnabled).isFalse()
    }

    @Test
    fun updateAwayTimeout_shouldUpdateDataStoreAndEmitNewValue() = runTest {
        settingsDataStore.updateAwayTimeout(30)

        assertThat(settingsDataStore.awayTimeoutSecFlow.first()).isEqualTo(30)
        assertThat(settingsDataStore.settingsFlow.first().awayTimeoutSec).isEqualTo(30)
    }

    @Test
    fun updateAutoBackupAfterPasswordLogin_shouldUpdateDataStore() = runTest {
        settingsDataStore.updateAutoBackupAfterPasswordLogin(false)

        assertThat(settingsDataStore.getAutoBackupAfterPasswordLogin()).isFalse()
    }

    @Test
    fun updatePasswordAfterXBiometricLogin_shouldUpdateDataStoreAndSyncPreferenceProvider() =
        runTest {
            settingsDataStore.updatePasswordAfterXBiometricLogin(15)

            assertThat(settingsDataStore.getPasswordAfterXBiometricLogins()).isEqualTo(15)
            val syncedPref = fakePreferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                -1
            )
            assertThat(syncedPref).isEqualTo(15)
        }
}
