package com.andryoga.safebox.ui.core

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the authenticator matrix of [getAllowedAuthenticators] per API level. The matrix encodes a
 * rule of androidx.biometric that only surfaces at runtime: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
 * is unsupported on API 28-29 and makes `canAuthenticate` fail with `BIOMETRIC_ERROR_UNSUPPORTED`
 * regardless of enrolment (issue #279).
 */
class DeviceSecurityAuthProviderTest {

    @Test
    fun `biometric only login requests BIOMETRIC_STRONG on every API level`() {
        for (sdkInt in Build.VERSION_CODES.N..Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            assertThat(getAllowedAuthenticators(allowDeviceCredential = false, sdkInt = sdkInt))
                .isEqualTo(BIOMETRIC_STRONG)
        }
    }

    @Test
    fun `device credential below API 30 requests BIOMETRIC_WEAK with DEVICE_CREDENTIAL`() {
        for (sdkInt in Build.VERSION_CODES.N until Build.VERSION_CODES.R) {
            assertThat(getAllowedAuthenticators(allowDeviceCredential = true, sdkInt = sdkInt))
                .isEqualTo(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        }
    }

    @Test
    fun `device credential on API 28 and 29 requests the only combination androidx supports there`() {
        for (sdkInt in listOf(Build.VERSION_CODES.P, Build.VERSION_CODES.Q)) {
            val authenticators = getAllowedAuthenticators(allowDeviceCredential = true, sdkInt = sdkInt)

            assertThat(authenticators).isEqualTo(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            assertThat(authenticators).isNotEqualTo(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        }
    }

    @Test
    fun `device credential from API 30 requests BIOMETRIC_STRONG with DEVICE_CREDENTIAL`() {
        for (sdkInt in Build.VERSION_CODES.R..Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            assertThat(getAllowedAuthenticators(allowDeviceCredential = true, sdkInt = sdkInt))
                .isEqualTo(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        }
    }
}
