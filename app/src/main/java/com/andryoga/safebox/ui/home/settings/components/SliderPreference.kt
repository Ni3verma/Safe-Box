package com.andryoga.safebox.ui.home.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * A settings row: a title that shows the current value, an optional body, and a [Slider] below.
 *
 * @param controlModifier applied to the [Slider] alone, not the row. The slider has no text of
 * its own, so this is where a `Modifier.testTag` belongs if a test needs to find it (see
 * `TestTags`).
 */
@Composable
fun SliderPreference(
    value: Int,
    title: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (newValue: Int) -> Unit,
    body: String? = null,
    controlModifier: Modifier = Modifier,
) {
    var localValue by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) {
        localValue = value.toFloat()
    }

    Column(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Text(
            String.format(title, localValue.toInt()),
            style = MaterialTheme.typography.titleLarge,
        )
        if (body.isNullOrBlank().not()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            modifier = controlModifier,
            valueRange = valueRange,
            steps = valueRange.endInclusive.toInt() - valueRange.start.toInt() - 1,
            onValueChangeFinished = {
                onValueChanged(localValue.toInt())
            }
        )
    }
}

@LightDarkModePreview
@Composable
private fun SliderPreferencePreview() {
    SafeBoxTheme {
        SliderPreference(
            value = 8,
            title = stringResource(R.string.settings_away_timeout_title),
            5f..20f,
            onValueChanged = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun SliderPreferenceWithBodyPreview() {
    SafeBoxTheme {
        SliderPreference(
            value = 8,
            title = stringResource(R.string.settings_away_timeout_title),
            5f..20f,
            onValueChanged = {},
            body = stringResource(R.string.settings_away_timeout_body),
        )
    }
}