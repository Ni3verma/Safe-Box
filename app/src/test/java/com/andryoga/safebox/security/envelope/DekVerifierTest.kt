package com.andryoga.safebox.security.envelope

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class DekVerifierTest {

    private val realDek = ByteArray(32) { index -> (index + 10).toByte() }
    private val substitutedDek = ByteArray(32) { index -> (index + 99).toByte() }

    @Test
    fun verify_withRealDek_shouldReturnTrue() {
        val verifier = DekVerifier.makeVerifier(realDek)

        assertThat(DekVerifier.verify(realDek, verifier)).isTrue()
    }

    @Test
    fun verify_withSubstitutedDek_shouldReturnFalse() {
        val verifier = DekVerifier.makeVerifier(realDek)

        assertThat(DekVerifier.verify(substitutedDek, verifier)).isFalse()
    }

    @Test
    fun verify_withTruncatedOrMalformedBlob_shouldReturnFalse() {
        val truncatedBlob = Base64.getEncoder().encodeToString(ByteArray(12) { 1 })

        assertThat(DekVerifier.verify(realDek, truncatedBlob)).isFalse()
        assertThat(DekVerifier.verify(realDek, "###not_base64###")).isFalse()
    }
}
