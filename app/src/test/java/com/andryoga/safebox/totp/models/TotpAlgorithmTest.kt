package com.andryoga.safebox.totp.models

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the lenient name resolution used on the restore path, where the algorithm name comes from
 * a backup file rather than from this build.
 */
class TotpAlgorithmTest {

    @Test
    fun fromNameOrDefault_knownName_resolvesToMatchingEntry() {
        assertThat(TotpAlgorithm.fromNameOrDefault("SHA256")).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(TotpAlgorithm.fromNameOrDefault("SHA512")).isEqualTo(TotpAlgorithm.SHA512)
    }

    @Test
    fun fromNameOrDefault_differentCasing_stillResolves() {
        assertThat(TotpAlgorithm.fromNameOrDefault("sha256")).isEqualTo(TotpAlgorithm.SHA256)
    }

    @Test
    fun fromNameOrDefault_unknownOrAbsentName_fallsBackToSha1() {
        // a backup written by a newer build must degrade, not fail the restore of every record.
        assertThat(TotpAlgorithm.fromNameOrDefault("SHA3")).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(TotpAlgorithm.fromNameOrDefault("")).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(TotpAlgorithm.fromNameOrDefault(null)).isEqualTo(TotpAlgorithm.SHA1)
    }
}
