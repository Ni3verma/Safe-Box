package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun TextPreference(
    title: String,
    body: String,
    onTap: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable {
                onTap()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(
            imageVector = icon ?: Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = title,
            modifier = Modifier.size(32.dp)
        )
    }
}

@LightDarkModePreview
@Composable
private fun TextPreferencePreview() {
    SafeBoxTheme {
        TextPreference(
            title = stringResource(R.string.settings_feedback_title),
            body = stringResource(R.string.settings_feedback_body),
            onTap = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun TextPreferenceLongTitlePreview() {
    SafeBoxTheme {
        TextPreference(
            title = stringResource(R.string.settings_feedback_title) + stringResource(R.string.settings_feedback_title) + stringResource(
                R.string.settings_feedback_title
            ),
            body = stringResource(R.string.settings_feedback_body),
            onTap = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun TextPreferenceWithIconPreview() {
    SafeBoxTheme {
        TextPreference(
            title = stringResource(R.string.settings_feedback_title) + stringResource(R.string.settings_feedback_title) + stringResource(
                R.string.settings_feedback_title
            ),
            body = stringResource(R.string.settings_feedback_body),
            onTap = {},
            icon = Icons.Outlined.Email,
        )
    }
}