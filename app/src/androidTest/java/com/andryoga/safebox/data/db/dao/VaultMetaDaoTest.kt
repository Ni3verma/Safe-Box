package com.andryoga.safebox.data.db.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class VaultMetaDaoTest {
    private lateinit var database: SafeBoxDatabase
    private lateinit var dao: VaultMetaDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java).build()
        dao = database.vaultMetaDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun get_whenKeyWasNeverWritten_shouldReturnNull() = runBlocking {
        assertThat(dao.get(VaultMetaKey.CRYPTO_VERSION)).isNull()
    }

    @Test
    fun upsert_newKey_shouldBeReadableByGet() = runBlocking {
        dao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))

        assertThat(dao.get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.LEGACY)
    }

    @Test
    fun upsert_existingKey_shouldReplaceValueInsteadOfFailingOnThePrimaryKey() = runBlocking {
        dao.upsert(VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, "old|salt"))

        dao.upsert(VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, "new|salt"))

        assertThat(dao.get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isEqualTo("new|salt")
    }

    @Test
    fun upsert_differentKeys_shouldKeepEachValueIndependently() = runBlocking {
        dao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
        dao.upsert(VaultMetaEntity(VaultMetaKey.LEGACY_PASSWORD_HASH, "hash|salt"))

        assertThat(dao.get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.LEGACY)
        assertThat(dao.get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isEqualTo("hash|salt")
    }
}
