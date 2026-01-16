package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.core.PulseButton
import com.andryoga.safebox.ui.core.ScrollBehaviorType
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenUiState
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun SingleRecordTopBarTitle(title: String) {
    Text(text = title)
}

@Composable
fun SingleRecordTopBarNavIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back_button)
        )
    }
}

@Composable
fun SingleRecordTopBarActions(
    uiState: SingleRecordScreenUiState.TopAppBarUiState,
    onSaveClick: () -> Unit,
) {
    if (uiState.isSaveButtonVisible) {
        PulseButton(
            textResId = R.string.save,
            enabled = uiState.isSaveButtonEnabled,
            onClick = onSaveClick
        )
    }
}

private fun getTopAppBarConfig(
    uiState: SingleRecordScreenUiState.TopAppBarUiState,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
): TopAppBarConfig {
    return TopAppBarConfig(
        title = { SingleRecordTopBarTitle(uiState.title) },
        navigationIcon = { SingleRecordTopBarNavIcon(onBackClick) },
        actions = { SingleRecordTopBarActions(uiState, onSaveClick) },
        scrollBehaviorType = ScrollBehaviorType.NONE
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@LightDarkModePreview
@Composable
private fun TopBarHappyCasePreview() {
    SafeBoxTheme {
        MyAppTopAppBar(
            config = getTopAppBarConfig(
                uiState = SingleRecordScreenUiState.TopAppBarUiState(
                    title = "Login",
                    isSaveButtonVisible = true,
                    isSaveButtonEnabled = true
                ),
                onSaveClick = {},
                onBackClick = {}
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@LightDarkModePreview
@Composable
private fun TopBarWithoutSaveButtonPreview() {
    SafeBoxTheme {
        MyAppTopAppBar(
            config = getTopAppBarConfig(
                uiState = SingleRecordScreenUiState.TopAppBarUiState(
                    title = "Login",
                    isSaveButtonVisible = false,
                ),
                onSaveClick = {},
                onBackClick = {}
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@LightDarkModePreview
@Composable
private fun TopBarWithDisabledSaveButtonPreview() {
    SafeBoxTheme {
        MyAppTopAppBar(
            config = getTopAppBarConfig(
                uiState = SingleRecordScreenUiState.TopAppBarUiState(
                    title = "Login",
                    isSaveButtonVisible = false,
                    isSaveButtonEnabled = true
                ),
                onSaveClick = {},
                onBackClick = {}
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@LightDarkModePreview
@Composable
private fun TopBarWithoutTitlePreview() {
    SafeBoxTheme {
        MyAppTopAppBar(
            config = getTopAppBarConfig(
                uiState = SingleRecordScreenUiState.TopAppBarUiState(
                    title = "",
                    isSaveButtonVisible = false,
                    isSaveButtonEnabled = true
                ),
                onSaveClick = {},
                onBackClick = {}
            )
        )
    }
}