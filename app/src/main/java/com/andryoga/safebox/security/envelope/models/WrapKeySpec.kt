package com.andryoga.safebox.security.envelope.models

import com.andryoga.safebox.data.db.models.VaultMetaKey

/**
 * Identifies which hardware-backed RSA-2048 wrapping key pair is targeted.
 *
 * @property wrappedDekKey `vault_meta` key storing the wrapped DEK blob for this key pair.
 * @property aliasSuffixKey `vault_meta` key storing the per-generation Keystore alias suffix.
 * @property wireName Analytics wire value identifying this wrap type.
 */
enum class WrapKeySpec(
    val wrappedDekKey: String,
    val aliasSuffixKey: String,
    val wireName: String,
) {
    BIOMETRIC(
        wrappedDekKey = VaultMetaKey.WRAPPED_DEK_BIOMETRIC,
        aliasSuffixKey = VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX,
        wireName = "biometric",
    ),
    DEVICE(
        wrappedDekKey = VaultMetaKey.WRAPPED_DEK_DEVICE,
        aliasSuffixKey = VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX,
        wireName = "device",
    ),
}
