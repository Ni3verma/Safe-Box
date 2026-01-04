package com.andryoga.safebox.ui.previewHelper

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light Mode"
)
@Preview(
    uiMode = UI_MODE_NIGHT_YES,
    name = "Night Mode"
)
annotation class LightDarkModePreview
