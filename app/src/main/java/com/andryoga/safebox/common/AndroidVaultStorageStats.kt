package com.andryoga.safebox.common

import android.content.Context
import android.os.StatFs
import com.andryoga.safebox.data.db.SafeBoxDatabase
import timber.log.Timber
import java.io.File

/**
 * [VaultStorageStats] over the real database file and the app-data partition.
 */
class AndroidVaultStorageStats(
    private val context: Context,
) : VaultStorageStats {

    override fun snapshot(): VaultStorageSnapshot = try {
        val dbFile = context.getDatabasePath(SafeBoxDatabase.DATABASE_NAME)
        // File.length() is 0 for a file that does not exist, so neither needs an exists() check.
        val walFile = File(dbFile.path + WAL_SUFFIX)
        // filesDir always exists and shares the partition with databases/, so StatFs cannot be
        // asked about a directory that Room has not created yet.
        val partition = StatFs(context.filesDir.path)
        VaultStorageSnapshot(
            dbSizeKb = (dbFile.length() + walFile.length()) / BYTES_PER_KIB,
            freeSpaceMb = partition.availableBytes / BYTES_PER_MIB,
        )
    } catch (e: RuntimeException) {
        // telemetry only: a missing number is the whole cost of a failed read
        Timber.w(e, "vault storage stats read failed")
        VaultStorageSnapshot.UNAVAILABLE
    }

    companion object {
        private const val WAL_SUFFIX = "-wal"
        private const val BYTES_PER_KIB = 1024L
        private const val BYTES_PER_MIB = 1024L * 1024L
    }
}
