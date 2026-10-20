package com.andryoga.safebox.security.interfaces

/**
 * Read-only view of what the phone can authenticate with, taken at a moment in time.
 *
 * Exists so ViewModels can report device capabilities to analytics without touching
 * `KeyguardManager`, `BiometricManager` or `PackageManager` themselves, and so tests can hand a
 * ViewModel any combination of capabilities.
 */
interface DeviceSecurityStatus {
    /**
     * Reads the current state in one call.
     *
     * @return the snapshot. Implementations never throw: a framework failure degrades to
     * [BiometricClass.UNAVAILABLE] and `false`, because callers log this on the unlock path and a
     * telemetry read must not be able to break a login.
     */
    fun snapshot(): DeviceSecuritySnapshot
}

/**
 * @property isDeviceSecure `KeyguardManager.isDeviceSecure`: a PIN, pattern or password is set.
 * @property biometricClass the strongest biometric the device can use right now.
 * @property hasStrongBox `PackageManager.FEATURE_STRONGBOX_KEYSTORE`; always `false` below API 28.
 */
data class DeviceSecuritySnapshot(
    val isDeviceSecure: Boolean,
    val biometricClass: BiometricClass,
    val hasStrongBox: Boolean,
)

/**
 * Strongest biometric authenticator usable right now, as `BiometricManager.canAuthenticate` reports
 * it for `BIOMETRIC_STRONG` and then `BIOMETRIC_WEAK`.
 *
 * @param analyticsValue Wire value of the `biometric_class` analytics param; append-only once
 * shipped.
 */
enum class BiometricClass(val analyticsValue: String) {
    /** A Class 3 biometric is enrolled and ready. */
    STRONG("strong"),

    /** Only a Class 2 biometric (typically face unlock) is enrolled and ready. */
    WEAK_ONLY("weak_only"),

    /** Hardware exists but nothing is enrolled, at either strength. */
    NONE_ENROLLED("none_enrolled"),

    /** No biometric hardware at all. */
    NO_HARDWARE("no_hardware"),

    /** Hardware busy, security update required, unsupported combination, or the read failed. */
    UNAVAILABLE("unavailable"),
}
