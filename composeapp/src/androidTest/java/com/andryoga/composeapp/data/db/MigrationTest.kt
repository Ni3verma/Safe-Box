package com.andryoga.composeapp.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.composeapp.data.db.Migration.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SafeBoxDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_4_5() {
        var db = helper.createDatabase(TEST_DB, 4)
        db.execSQL(
            """
        INSERT INTO bank_card_data (title, number, expiryDate, creationDate, updateDate) 
        VALUES ('Test Card', '12345678', '11/28', 1704312000, 1704312000)
        """.trimIndent()
        )

        db.execSQL(
            """
        INSERT INTO bank_card_data (title, number, expiryDate, creationDate, updateDate) 
        VALUES ('Space Card', '87654321', ' 05/25 ', 1704312000, 1704312000)
        """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        val cursor = db.query("SELECT title, expiryDate FROM bank_card_data ORDER BY title ASC")

        cursor.moveToFirst()
        assertEquals("0525", cursor.getString(cursor.getColumnIndex("expiryDate")))

        cursor.moveToNext()
        assertEquals("1128", cursor.getString(cursor.getColumnIndex("expiryDate")))

        cursor.close()
    }
}