@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.test.fakes.FirebaseCrashlyticsTestHelper
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.slot
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
    lateinit var userDetailsDaoSecure: UserDetailsDaoSecure

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    private lateinit var preferenceProvider: FakePreferenceProvider
    private lateinit var mockCrashlytics: FirebaseCrashlytics
    private lateinit var repository: UserDetailsRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockCrashlytics = FirebaseCrashlyticsTestHelper.setupMockCrashlytics()
        preferenceProvider = FakePreferenceProvider()
        repository = UserDetailsRepositoryImpl(
            userDetailsDaoSecure = userDetailsDaoSecure,
            preferenceProvider = preferenceProvider,
            settingsDataStore = settingsDataStore
        )
    }

    @After
    fun tearDown() {
        FirebaseCrashlyticsTestHelper.tearDownMockCrashlytics()
    }

    @Test
    fun insertUserDetailsData_shouldSetCrashlyticsUidAndInsertEntityToDaoSecure() = runTest {
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDaoSecure.insertUserDetailsData(capture(slot)) } returns Unit

        repository.insertUserDetailsData(password = "secretPass123", hint = "favorite color")

        verify { mockCrashlytics.setUserId(any()) }
        verify { mockCrashlytics.setCustomKey(CommonConstants.CRASHLYTICS_KEY_UID, any<String>()) }

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.password).isEqualTo("secretPass123")
        assertThat(slot.captured.hint).isEqualTo("favorite color")
        assertThat(slot.captured.uid).isNotEmpty()
    }

    @Test
    fun checkPassword_shouldDelegateToUserDetailsDaoSecure() = runTest {
        coEvery { userDetailsDaoSecure.checkPassword("correctPass") } returns true
        coEvery { userDetailsDaoSecure.checkPassword("wrongPass") } returns false

        assertThat(repository.checkPassword("correctPass")).isTrue()
        assertThat(repository.checkPassword("wrongPass")).isFalse()
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

            repository.onAuthSuccess(withBiometric = true)

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
}
