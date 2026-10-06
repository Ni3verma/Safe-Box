package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import com.andryoga.safebox.ui.core.motion.MotionTokens
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenUiState
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Top app bar of the single record screen: record title, back arrow and, in edit / create mode,
 * the save button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleRecordTopAppBar(
    uiState: SingleRecordScreenUiState.TopAppBarUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    MyAppTopAppBar(
        title = { SingleRecordTopBarTitle(uiState.title) },
        navigationIcon = { SingleRecordTopBarNavIcon(onBackClick) },
        actions = { SingleRecordTopBarActions(uiState, onSaveClick) },
    )
}

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
    // Pop the save button in when the screen enters edit mode so the eye is drawn to the new
    // affordance. The exit mirrors it for symmetry; saving closes the screen, so in practice only
    // the entrance is ever seen.
    AnimatedVisibility(
        visible = uiState.isSaveButtonVisible,
        enter = fadeIn() + scaleIn(initialScale = MotionTokens.POP_IN_INITIAL_SCALE),
        exit = fadeOut() + scaleOut(targetScale = MotionTokens.POP_IN_INITIAL_SCALE),
    ) {
        PulseButton(
            textResId = R.string.save,
            enabled = uiState.isSaveButtonEnabled,
            onClick = onSaveClick
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarHappyCasePreview() {
    SafeBoxTheme {
        SingleRecordTopAppBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonVisible = true,
                isSaveButtonEnabled = true,
            ),
            onBackClick = {},
            onSaveClick = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithoutSaveButtonPreview() {
    SafeBoxTheme {
        SingleRecordTopAppBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonVisible = false,
            ),
            onBackClick = {},
            onSaveClick = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithDisabledSaveButtonPreview() {
    SafeBoxTheme {
        SingleRecordTopAppBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "Login",
                isSaveButtonVisible = false,
                isSaveButtonEnabled = true,
            ),
            onBackClick = {},
            onSaveClick = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun TopBarWithoutTitlePreview() {
    SafeBoxTheme {
        SingleRecordTopAppBar(
            uiState = SingleRecordScreenUiState.TopAppBarUiState(
                title = "",
                isSaveButtonVisible = false,
                isSaveButtonEnabled = true,
            ),
            onBackClick = {},
            onSaveClick = {},
        )
    }
}