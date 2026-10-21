package com.andryoga.safebox.common

/**
 * Size of the vault on disk and the headroom left on its partition.
 *
 * Read once per unlock session for analytics only (`vault_stats`). Behind an interface so the
 * ViewModel that logs it needs no `Context` and tests can supply any numbers.
 */
interface VaultStorageStats {
    /**
     * Reads both figures now.
     *
     * @return the snapshot, with [VaultStorageSnapshot.UNKNOWN] in both fields when the read
     * failed. Implementations never throw: this is telemetry, and a storage read that fails must
     * cost nothing more than a missing number.
     */
    fun snapshot(): VaultStorageSnapshot
}

/**
 * @property dbSizeKb the Room database file plus its write-ahead log, in KiB; 0 before the first
 * open.
 * @property freeSpaceMb free space on the app-data partition, in MiB.
 */
data class VaultStorageSnapshot(
    val dbSizeKb: Long,
    val freeSpaceMb: Long,
) {
    companion object {
        /** Either figure when its read failed: still numeric, so the param is always present, and impossible as a real size. */
        const val UNKNOWN = -1L

        /** The snapshot reported when nothing could be read. */
        val UNAVAILABLE = VaultStorageSnapshot(dbSizeKb = UNKNOWN, freeSpaceMb = UNKNOWN)
    }
}
