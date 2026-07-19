@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.test.fakes.FakeHashingUtils
import com.andryoga.safebox.test.fakes.FakeSymmetricKeyUtils
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserDetailsDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDao: UserDetailsDao

    private lateinit var hashingUtils: FakeHashingUtils
    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var userDetailsDaoSecure: UserDetailsDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        hashingUtils = FakeHashingUtils()
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        userDetailsDaoSecure = UserDetailsDaoSecure(
            userDetailsDao = userDetailsDao,
            hashingUtils = hashingUtils,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    @Test
    fun insertUserDetailsData_shouldHashPasswordAndEncryptHintBeforeDaoInsert() = runTest {
        val inputEntity = TestFixtures.createTestUserDetailsEntity(
            password = "rawPassword",
            hint = "myHint"
        )
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.password).isEqualTo("HASH[rawPassword]|FAKE_SALT")
        assertThat(slot.captured.hint).isEqualTo("ENC[myHint]")
        assertThat(slot.captured.uid).isEqualTo(inputEntity.uid)
    }

    @Test
    fun insertUserDetailsData_whenHintIsNull_shouldHashPasswordAndKeepNullHint() = runTest {
        val inputEntity = TestFixtures.createTestUserDetailsEntity(
            password = "plainPassword",
            hint = null
        )
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(inputEntity)

        assertThat(slot.captured.password).isEqualTo("HASH[plainPassword]|FAKE_SALT")
        assertThat(slot.captured.hint).isNull()
    }

    @Test
    fun getUserPassword_shouldDelegateToUserDetailsDao() = runTest {
        coEvery { userDetailsDao.getUserPassword() } returns "HASH[storedPass]|FAKE_SALT"

        val password = userDetailsDaoSecure.getUserPassword()

        assertThat(password).isEqualTo("HASH[storedPass]|FAKE_SALT")
    }

    @Test
    fun getHint_whenHintIsPresent_shouldDecryptHint() = runTest {
        coEvery { userDetailsDao.getHint() } returns "ENC[secretHint]"

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("secretHint")
    }

    @Test
    fun getHint_whenHintIsNull_shouldReturnNull() = runTest {
        coEvery { userDetailsDao.getHint() } returns null

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isNull()
    }

    @Test
    fun getUid_shouldDelegateToUserDetailsDao() = runTest {
        coEvery { userDetailsDao.getUid() } returns "unique-user-id"

        val uid = userDetailsDaoSecure.getUid()

        assertThat(uid).isEqualTo("unique-user-id")
    }

    @Test
    fun checkPassword_shouldRetrieveStoredPasswordHashAndCompareViaHashingUtils() = runTest {
        coEvery { userDetailsDao.getUserPassword() } returns "HASH[correctPass]|FAKE_SALT"

        val matchesCorrect = userDetailsDaoSecure.checkPassword("correctPass")
        val matchesWrong = userDetailsDaoSecure.checkPassword("wrongPass")

        assertThat(matchesCorrect).isTrue()
        assertThat(matchesWrong).isFalse()
    }
}
