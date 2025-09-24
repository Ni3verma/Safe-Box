package com.andryoga.composeapp.ui.singleRecord.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.PulseButton
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenUiState
import com.andryoga.composeapp.ui.theme.SafeBoxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    uiState: SingleRecordScreenUiState.TopAppBarUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(text = uiState.title)
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back_button)
                )
            }
        },
        actions = {
            if (uiState.isSaveButtonVisible) {
                PulseButton(
                    textResId = R.string.save,
                    enabled = uiState.isSaveButtonEnabled,
                    onClick = onSaveClick
                )
            }
        }
    )
}

@LightDarkModePreview
@Composable
private fun TopBarHappyCasePreview() {
    SafeBoxTheme {
        TopBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonEnabled = true,
                isSaveButtonVisible = true
            ),
            onBackClick = {},
            onSaveClick = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithoutSaveButtonPreview() {
    SafeBoxTheme {
        TopBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonVisible = false
            ),
            onBackClick = {},
            onSaveClick = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithDisabledSaveButtonPreview() {
    SafeBoxTheme {
        TopBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonEnabled = false,
                isSaveButtonVisible = true
            ),
            onBackClick = {},
            onSaveClick = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithoutTitlePreview() {
    SafeBoxTheme {
        TopBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "",
                isSaveButtonEnabled = false,
                isSaveButtonVisible = true
            ),
            onBackClick = {},
            onSaveClick = {}
        )
    }
}