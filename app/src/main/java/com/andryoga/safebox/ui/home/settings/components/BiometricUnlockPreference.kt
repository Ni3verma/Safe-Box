package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.TestTags
import com.andryoga.safebox.ui.home.settings.BiometricUnlockSwitch
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * The fingerprint-unlock row: absent without hardware or in a legacy session, disabled until a
 * strong biometric is enrolled.
 */
@Composable
internal fun BiometricUnlockPreference(
    switch: BiometricUnlockSwitch,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (switch == BiometricUnlockSwitch.Hidden) return
    val isAvailable = switch is BiometricUnlockSwitch.Available
    SwitchPreference(
        title = stringResource(R.string.settings_biometric_unlock_title),
        checked = (switch as? BiometricUnlockSwitch.Available)?.isOn == true,
        onCheckedChange = onCheckedChange,
        body = if (isAvailable) {
            stringResource(R.string.settings_biometric_unlock_body)
        } else {
            stringResource(R.string.settings_biometric_unlock_unavailable_body)
        },
        controlModifier = Modifier.testTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH),
        enabled = isAvailable,
    )
}

@LightDarkModePreview
@Composable
private fun BiometricUnlockPreferenceOnPreview() {
    SafeBoxTheme {
        BiometricUnlockPreference(switch = BiometricUnlockSwitch.Available(isOn = true), onCheckedChange = {})
    }
}

@LightDarkModePreview
@Composable
private fun BiometricUnlockPreferenceOffPreview() {
    SafeBoxTheme {
        BiometricUnlockPreference(switch = BiometricUnlockSwitch.Available(isOn = false), onCheckedChange = {})
    }
}

@LightDarkModePreview
@Composable
private fun BiometricUnlockPreferenceUnavailablePreview() {
    SafeBoxTheme {
        BiometricUnlockPreference(switch = BiometricUnlockSwitch.Unavailable, onCheckedChange = {})
    }
}
