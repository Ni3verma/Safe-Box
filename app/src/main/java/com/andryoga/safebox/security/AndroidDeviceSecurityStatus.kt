package com.andryoga.safebox.security

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import timber.log.Timber

/**
 * [DeviceSecurityStatus] over the framework managers.
 *
 * Every read is guarded: these are binder calls into system services, and this snapshot is taken
 * on the unlock path purely for analytics, so a failure degrades the value instead of propagating.
 */
class AndroidDeviceSecurityStatus(
    private val context: Context,
) : DeviceSecurityStatus {

    override fun snapshot(): DeviceSecuritySnapshot = DeviceSecuritySnapshot(
        isDeviceSecure = readOrDefault(false) {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        },
        biometricClass = readOrDefault(BiometricClass.UNAVAILABLE) {
            val biometricManager = BiometricManager.from(context)
            classifyBiometric(
                strongResult = biometricManager.canAuthenticate(BIOMETRIC_STRONG),
                weakResult = biometricManager.canAuthenticate(BIOMETRIC_WEAK),
            )
        },
        hasStrongBox = readOrDefault(false) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        },
    )

    private inline fun <T> readOrDefault(default: T, read: () -> T): T = try {
        read()
    } catch (e: RuntimeException) {
        // telemetry only: a flaky system service must never surface on the unlock path
        Timber.w(e, "device security status read failed")
        default
    }

    companion object {
        /**
         * Collapses the two `canAuthenticate` answers into one [BiometricClass].
         *
         * Order matters: a strong success wins; otherwise a weak success means only a Class 2
         * biometric is ready; otherwise the strong result explains why nothing is usable.
         *
         * @param strongResult `canAuthenticate(BIOMETRIC_STRONG)`.
         * @param weakResult `canAuthenticate(BIOMETRIC_WEAK)`.
         */
        fun classifyBiometric(strongResult: Int, weakResult: Int): BiometricClass = when {
            strongResult == BiometricManager.BIOMETRIC_SUCCESS -> BiometricClass.STRONG
            weakResult == BiometricManager.BIOMETRIC_SUCCESS -> BiometricClass.WEAK_ONLY
            strongResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricClass.NONE_ENROLLED
            strongResult == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricClass.NO_HARDWARE
            else -> BiometricClass.UNAVAILABLE
        }
    }
}
