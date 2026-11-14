package com.andryoga.safebox.worker.backup

import java.io.IOException
import javax.crypto.BadPaddingException

/**
 * A backup file whose container has been parsed far enough to know its format version, with the
 * records still encrypted. Splitting the two steps lets the restore reject a too-new file, and
 * report the version of a failed one, before any password work.
 */
interface OpenedBackup {
    /** Format version from the unencrypted part of the file, read unsigned (`0..255`). */
    val version: Int

    /**
     * Derives the key, decrypts and parses the records.
     *
     * @param password Backup password; not modified, the caller zeroes it.
     * @return The records of every type the file carries.
     * @throws BadPaddingException When the password is wrong.
     * @throws CorruptBackupException When the file is damaged or not a backup.
     */
    fun decode(password: CharArray): DecodedBackup
}

/**
 * The file is damaged, truncated, oversized or not a Safe-Box backup. An [IOException] so the
 * restore maps it to `CORRUPT_OR_INVALID_FILE` like any other unreadable input.
 */
class CorruptBackupException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
