@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import androidx.room.withTransaction
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.test.fakes.FakeHashingUtils
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.test.fakes.FirebaseCrashlyticsTestHelper
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserDetailsRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var database: SafeBoxDatabase

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDaoSecure: UserDetailsDaoSecure

    @MockK(relaxUnitFun = true)
    lateinit var vaultMetaDao: VaultMetaDao

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    @RelaxedMockK
    lateinit var analyticsHelper: AnalyticsHelper

    private lateinit var hashingUtils: FakeHashingUtils
    private lateinit var preferenceProvider: FakePreferenceProvider
    private lateinit var mockCrashlytics: FirebaseCrashlytics
    private lateinit var repository: UserDetailsRepositoryImpl

    /** Every `withTransaction` call on the mocked database runs its block inline and is counted. */
    private var transactionCount = 0

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockCrashlytics = FirebaseCrashlyticsTestHelper.setupMockCrashlytics()
        hashingUtils = FakeHashingUtils()
        preferenceProvider = FakePreferenceProvider()

        mockkStatic(ROOM_DATABASE_EXTENSIONS)
        val transaction = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers {
            transactionCount++
            transaction.captured.invoke()
        }

        repository = UserDetailsRepositoryImpl(
            database = database,
            userDetailsDaoSecure = userDetailsDaoSecure,
            vaultMetaDao = vaultMetaDao,
            hashingUtils = hashingUtils,
            preferenceProvider = preferenceProvider,
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_EXTENSIONS)
        FirebaseCrashlyticsTestHelper.tearDownMockCrashlytics()
    }

    @Test
    fun checkPassword_shouldCompareAgainstLegacyPasswordHashFromVaultMeta() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.LEGACY_PASSWORD_HASH) } returns "HASH[correctPass]|FAKE_SALT"

        assertThat(repository.checkPassword("correctPass")).isTrue()
        assertThat(repository.checkPassword("wrongPass")).isFalse()
    }

    @Test
    fun checkPassword_whenLegacyPasswordHashIsMissing_shouldAssertInDebugBuilds() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.LEGACY_PASSWORD_HASH) } returns null

        val outcome = runCatching { repository.checkPassword("anyPass") }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(Exceptions.DebugFatalException::class.java)
    }

    @Test
    fun onAuthSuccess_withBiometricTrue_shouldDecrementAllowedBiometricCountAndIncrementTotalLoginCount() =
        runTest {
            coEvery { userDetailsDaoSecure.getUid() } returns "uid-biometric-1"
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                5
            )
            preferenceProvider.upsertIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 2)

            val loginOrdinal = repository.onAuthSuccess(withBiometric = true)

            verify { mockCrashlytics.setUserId("uid-biometric-1") }
            verify {
                mockCrashlytics.setCustomKey(
                    CommonConstants.CRASHLYTICS_KEY_UID,
                    "uid-biometric-1"
                )
            }

            val remaining = preferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                -1
            )
            val total = preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, -1)

            assertThat(remaining).isEqualTo(4)
            assertThat(total).isEqualTo(3)
            // the pref is stored post-increment, so a stored 2 means this was the second login
            assertThat(loginOrdinal).isEqualTo(2)
        }

    @Test
    fun onAuthSuccess_withBiometricTrue_whenCountAlreadyZero_shouldNotUnderflowBelowZero() =
        runTest {
            coEvery { userDetailsDaoSecure.getUid() } returns "uid-zero"
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                0
            )
            preferenceProvider.upsertIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)

            repository.onAuthSuccess(withBiometric = true)

            val remaining = preferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                -1
            )
            assertThat(remaining).isEqualTo(0)
        }

    @Test
    fun onAuthSuccess_withBiometricFalse_shouldResetBiometricCountFromSettingsDataStore() =
        runTest {
            coEvery { userDetailsDaoSecure.getUid() } returns "uid-pass-login"
            coEvery { settingsDataStore.getPasswordAfterXBiometricLogins() } returns 10
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                1
            )
            preferenceProvider.upsertIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 4)

            repository.onAuthSuccess(withBiometric = false)

            val remaining = preferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                -1
            )
            val total = preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, -1)

            assertThat(remaining).isEqualTo(10)
            assertThat(total).isEqualTo(5)
        }

    @Test
    fun getHint_shouldReturnHintFromDaoSecure() = runTest {
        coEvery { userDetailsDaoSecure.getHint() } returns "first pet"

        val hint = repository.getHint()

        assertThat(hint).isEqualTo("first pet")
    }

    @Test
    fun shouldStartBiometricAuthFlow_whenRemainingCountGreaterThanZero_shouldReturnTrue() =
        runTest {
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                3
            )

            val result = repository.shouldStartBiometricAuthFlow()

            assertThat(result).isTrue()
        }

    @Test
    fun shouldStartBiometricAuthFlow_whenRemainingCountIsZeroOrNegative_shouldReturnFalse() =
        runTest {
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                0
            )

            val result = repository.shouldStartBiometricAuthFlow()

            assertThat(result).isFalse()
        }

    @Test
    fun updatePasswordAndHint_shouldWriteNewHashAndHintInOneTransactionAndResetBiometricCount() =
        runTest {
            coEvery { settingsDataStore.getPasswordAfterXBiometricLogins() } returns 10
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                1
            )

            repository.updatePasswordAndHint("NewPassword@@123", "new hint")

            assertThat(transactionCount).isEqualTo(1)
            coVerifyOrder {
                vaultMetaDao.upsert(
                    VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, "HASH[NewPassword@@123]|FAKE_SALT"),
                )
                userDetailsDaoSecure.updateHint("new hint", any())
            }
            val remaining = preferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                -1
            )

            assertThat(remaining).isEqualTo(10)
        }

    private companion object {
        /** File facade that hosts the `RoomDatabase.withTransaction` extension. */
        const val ROOM_DATABASE_EXTENSIONS = "androidx.room.RoomDatabaseKt"
    }
}
