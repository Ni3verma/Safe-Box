@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.test.fakes.FakeSymmetricKeyUtils
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SecureNoteDataDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var secureNoteDataDao: SecureNoteDataDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var secureNoteDataDaoSecure: SecureNoteDataDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        secureNoteDataDaoSecure = SecureNoteDataDaoSecure(
            secureNoteDataDao = secureNoteDataDao,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    @Test
    fun upsertSecretNoteData_shouldEncryptNotesBeforePassingToDao() = runTest {
        val inputEntity = TestFixtures.createTestSecureNoteDataEntity(
            title = "Master Recovery Phrase",
            notes = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        )
        val slot = slot<SecureNoteDataEntity>()
        coEvery { secureNoteDataDao.upsertSecretNoteData(capture(slot)) } returns Unit

        secureNoteDataDaoSecure.upsertSecretNoteData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.title).isEqualTo("Master Recovery Phrase")
        assertThat(slot.captured.notes).isEqualTo("ENC[abandon ability able about above absent absorb abstract absurd abuse access accident]")
    }

    @Test
    fun insertMultipleSecureNoteData_shouldEncryptNotesInAllEntities() = runTest {
        val inputList = listOf(
            TestFixtures.createTestSecureNoteDataEntity(key = 1, notes = "note1"),
            TestFixtures.createTestSecureNoteDataEntity(key = 2, notes = "note2")
        )
        val slot = slot<List<SecureNoteDataEntity>>()
        every { secureNoteDataDao.insertMultipleSecureNoteData(capture(slot)) } returns Unit

        secureNoteDataDaoSecure.insertMultipleSecureNoteData(inputList)

        assertThat(slot.captured).hasSize(2)
        assertThat(slot.captured[0].notes).isEqualTo("ENC[note1]")
        assertThat(slot.captured[1].notes).isEqualTo("ENC[note2]")
    }

    @Test
    fun getAllSecretNoteData_shouldReturnFlowFromDaoDirectly() = runTest {
        val searchList = listOf(
            TestFixtures.createTestSearchSecureNoteData(key = 1, title = "House Code")
        )
        every { secureNoteDataDao.getAllSecretNoteData() } returns flowOf(searchList)

        secureNoteDataDaoSecure.getAllSecretNoteData().test {
            val items = awaitItem()
            assertThat(items).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getSecretNoteDataByKey_shouldDecryptRetrievedEntityNotes() = runTest {
        val encryptedEntity = TestFixtures.createTestSecureNoteDataEntity(
            key = 33,
            title = "Server Root Password",
            notes = "ENC[SuperSecretRoot123!]"
        )
        coEvery { secureNoteDataDao.getSecretNoteDataByKey(33) } returns encryptedEntity

        val decrypted = secureNoteDataDaoSecure.getSecretNoteDataByKey(33)

        assertThat(decrypted.key).isEqualTo(33)
        assertThat(decrypted.title).isEqualTo("Server Root Password")
        assertThat(decrypted.notes).isEqualTo("SuperSecretRoot123!")
    }

    @Test
    fun deleteSecretNoteDataByKey_shouldDelegateToDao() = runTest {
        secureNoteDataDaoSecure.deleteSecretNoteDataByKey(33)

        coVerify(exactly = 1) { secureNoteDataDao.deleteSecretNoteDataByKey(33) }
    }

    @Test
    fun exportAllData_shouldDecryptNotesInExportSecureNoteDataList() = runTest {
        val encryptedExport = listOf(
            TestFixtures.createTestExportSecureNoteData(
                title = "Exported Note",
                notes = "ENC[Confidential memo text]"
            )
        )
        coEvery { secureNoteDataDao.exportAllData() } returns encryptedExport

        val exported = secureNoteDataDaoSecure.exportAllData()

        assertThat(exported).hasSize(1)
        assertThat(exported[0].title).isEqualTo("Exported Note")
        assertThat(exported[0].notes).isEqualTo("Confidential memo text")
    }

    @Test
    fun deleteAllData_shouldDelegateToDao() {
        secureNoteDataDaoSecure.deleteAllData()

        verify(exactly = 1) { secureNoteDataDao.deleteAllData() }
    }
}
