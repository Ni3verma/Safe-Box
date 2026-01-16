package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (body.isNullOrBlank().not()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            }
        )
    }
}

@LightDarkModePreview
@Composable
private fun SwitchPreferenceOnPreview() {
    SafeBoxTheme {
        SwitchPreference(
            title = stringResource(R.string.settings_privacy_enabled_title),
            checked = true,
            onCheckedChange = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SwitchPreferenceLongTitlePreview() {
    SafeBoxTheme {
        SwitchPreference(
            title = stringResource(R.string.settings_privacy_enabled_title) + stringResource(R.string.settings_privacy_enabled_title) + stringResource(
                R.string.settings_privacy_enabled_title
            ),
            checked = true,
            onCheckedChange = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SwitchPreferenceOffPreview() {
    SafeBoxTheme {
        SwitchPreference(
            title = stringResource(R.string.settings_privacy_enabled_title),
            checked = false,
            onCheckedChange = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SwitchPreferenceWithBodyPreview() {
    SafeBoxTheme {
        SwitchPreference(
            title = stringResource(R.string.settings_privacy_enabled_title),
            checked = true,
            onCheckedChange = {},
            body = stringResource(R.string.settings_privacy_enabled_body),
        )
    }
}
