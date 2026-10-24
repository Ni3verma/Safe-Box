package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class VaultRepositoryImplTest {

    @MockK
    lateinit var vaultMetaDao: VaultMetaDao

    @MockK
    lateinit var userDetailsDao: UserDetailsDao

    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = VaultRepositoryImpl(vaultMetaDao, userDetailsDao)
    }

    @Test
    fun hasVault_whenCryptoVersionIsEnvelope_shouldBeTrueWithoutReadingUserDetails() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE

        assertThat(repository.hasVault()).isTrue()
        coVerify(exactly = 0) { userDetailsDao.getUserCount() }
    }

    @Test
    fun hasVault_whenCryptoVersionIsLegacy_shouldBeTrue() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.LEGACY

        assertThat(repository.hasVault()).isTrue()
    }

    @Test
    fun hasVault_whenNoCryptoVersionRowAndNoUserRow_shouldBeFalse() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns null
        coEvery { userDetailsDao.getUserCount() } returns 0

        assertThat(repository.hasVault()).isFalse()
    }

    @Test
    fun hasVault_whenAUserRowHasNoCryptoVersionRow_shouldAssertInDebug() = runTest {
        // Cannot occur after MIGRATION_5_6; release builds answer true (Login, never Signup).
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns null
        coEvery { userDetailsDao.getUserCount() } returns 1

        val outcome = runCatching { repository.hasVault() }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(Exceptions.DebugFatalException::class.java)
    }
}
