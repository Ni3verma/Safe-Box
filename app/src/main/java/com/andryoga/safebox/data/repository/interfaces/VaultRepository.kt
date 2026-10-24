package com.andryoga.safebox.data.repository.interfaces

/** The vault as a whole (existence now, wrapping from MR 5), unlike the per-type record repositories. */
interface VaultRepository {
    /**
     * Whether a vault exists on disk, from Room alone — never from a preference, so a lost or
     * flipped one cannot route an existing vault into signup.
     */
    suspend fun hasVault(): Boolean
}
