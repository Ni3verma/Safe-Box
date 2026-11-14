package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus

/**
 * Scriptable [DeviceSecurityStatus] for suites that need a secured device with a strong biometric
 * on the CI image, which has neither. Bound per class with `@UninstallModules(TelemetryModule::class)`
 * and `@BindValue`, so every other suite keeps reading the real device.
 */
class FakeDeviceSecurityStatus(
    var isDeviceSecure: Boolean = true,
    var biometricClass: BiometricClass = BiometricClass.STRONG,
) : DeviceSecurityStatus {
    override fun snapshot() = DeviceSecuritySnapshot(
        isDeviceSecure = isDeviceSecure,
        biometricClass = biometricClass,
        hasStrongBox = false,
    )
}
