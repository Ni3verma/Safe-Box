package com.andryoga.safebox.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
import androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import com.andryoga.safebox.security.AndroidDeviceSecurityStatus.Companion.classifyBiometric
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

class AndroidDeviceSecurityStatusTest {

    private val context = mockk<Context>()

    @Before
    fun setUp() {
        mockkStatic(BiometricManager::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun classifyBiometric_strongSuccess_shouldBeStrongRegardlessOfWeak() {
        assertThat(classifyBiometric(BIOMETRIC_SUCCESS, BIOMETRIC_SUCCESS)).isEqualTo(BiometricClass.STRONG)
        assertThat(classifyBiometric(BIOMETRIC_SUCCESS, BIOMETRIC_ERROR_NONE_ENROLLED)).isEqualTo(BiometricClass.STRONG)
    }

    @Test
    fun classifyBiometric_onlyWeakSucceeds_shouldBeWeakOnly() {
        assertThat(classifyBiometric(BIOMETRIC_ERROR_NONE_ENROLLED, BIOMETRIC_SUCCESS)).isEqualTo(BiometricClass.WEAK_ONLY)
        assertThat(classifyBiometric(BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_SUCCESS)).isEqualTo(BiometricClass.WEAK_ONLY)
        assertThat(classifyBiometric(BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_SUCCESS)).isEqualTo(BiometricClass.WEAK_ONLY)
    }

    @Test
    fun classifyBiometric_nothingEnrolled_shouldBeNoneEnrolled() {
        assertThat(classifyBiometric(BIOMETRIC_ERROR_NONE_ENROLLED, BIOMETRIC_ERROR_NONE_ENROLLED))
            .isEqualTo(BiometricClass.NONE_ENROLLED)
    }

    @Test
    fun classifyBiometric_noHardware_shouldBeNoHardware() {
        assertThat(classifyBiometric(BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_NO_HARDWARE))
            .isEqualTo(BiometricClass.NO_HARDWARE)
    }

    @Test
    fun classifyBiometric_otherStrongErrors_shouldBeUnavailable() {
        listOf(
            BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BIOMETRIC_ERROR_UNSUPPORTED,
            BIOMETRIC_STATUS_UNKNOWN,
        ).forEach { strongResult ->
            assertThat(classifyBiometric(strongResult, strongResult)).isEqualTo(BiometricClass.UNAVAILABLE)
        }
    }

    @Test
    fun snapshot_shouldReadKeyguardAndBiometricManager() {
        val keyguardManager = mockk<KeyguardManager> { every { isDeviceSecure } returns true }
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        val biometricManager = mockk<BiometricManager> {
            every { canAuthenticate(BIOMETRIC_STRONG) } returns BIOMETRIC_ERROR_NONE_ENROLLED
            every { canAuthenticate(BIOMETRIC_WEAK) } returns BIOMETRIC_SUCCESS
        }
        every { BiometricManager.from(context) } returns biometricManager

        val snapshot = AndroidDeviceSecurityStatus(context).snapshot()

        assertThat(snapshot.isDeviceSecure).isTrue()
        assertThat(snapshot.biometricClass).isEqualTo(BiometricClass.WEAK_ONLY)
        // SDK_INT is 0 on the JVM, so the StrongBox branch short-circuits before touching PackageManager
        assertThat(snapshot.hasStrongBox).isFalse()
    }

    @Test
    fun snapshot_whenEverySystemServiceThrows_shouldDegradeToDefaultsInsteadOfPropagating() {
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } throws SecurityException("binder down")
        every { BiometricManager.from(context) } throws IllegalStateException("no biometric service")
        every { context.packageManager } throws RuntimeException("pm died")

        val snapshot = AndroidDeviceSecurityStatus(context).snapshot()

        assertThat(snapshot.isDeviceSecure).isFalse()
        assertThat(snapshot.biometricClass).isEqualTo(BiometricClass.UNAVAILABLE)
        assertThat(snapshot.hasStrongBox).isFalse()
    }

    @Test
    fun snapshot_whenOnlyBiometricReadThrows_shouldKeepTheOtherFields() {
        val keyguardManager = mockk<KeyguardManager> { every { isDeviceSecure } returns true }
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        every { BiometricManager.from(context) } throws IllegalStateException("no biometric service")

        val snapshot = AndroidDeviceSecurityStatus(context).snapshot()

        assertThat(snapshot.isDeviceSecure).isTrue()
        assertThat(snapshot.biometricClass).isEqualTo(BiometricClass.UNAVAILABLE)
    }
}
