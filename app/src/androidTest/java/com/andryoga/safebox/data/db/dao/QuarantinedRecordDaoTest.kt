package com.andryoga.safebox.data.db.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.entity.QuarantinedRecordEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Date

class QuarantinedRecordDaoTest {
    private lateinit var database: SafeBoxDatabase
    private lateinit var dao: QuarantinedRecordDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SafeBoxDatabase::class.java).build()
        dao = database.quarantinedRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun count_onEmptyTable_shouldBeZero() = runBlocking {
        assertThat(dao.count()).isEqualTo(0)
    }

    @Test
    fun insert_shouldAutoGenerateIdsSoRowsFromTheSameSourceNeverCollide() = runBlocking {
        dao.insert(quarantinedRow(sourceKey = 7))
        dao.insert(quarantinedRow(sourceKey = 7))

        assertThat(dao.count()).isEqualTo(2)
    }

    @Test
    fun deleteAll_shouldLeaveTableEmpty() = runBlocking {
        dao.insert(quarantinedRow(sourceKey = 1))
        dao.insert(quarantinedRow(sourceKey = 2))

        dao.deleteAll()

        assertThat(dao.count()).isEqualTo(0)
    }

    private fun quarantinedRow(sourceKey: Int) = QuarantinedRecordEntity(
        sourceTable = "login_data",
        sourceKey = sourceKey,
        rowJson = """{"title":"b64|b64"}""",
        reason = "aead_bad_tag",
        createdAt = Date(1700000000000L),
    )
}
