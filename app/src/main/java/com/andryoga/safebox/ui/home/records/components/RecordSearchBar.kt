package com.andryoga.safebox.ui.home.records.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.core.ScrollBehaviorType
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.home.records.RecordScreenAction
import com.andryoga.safebox.ui.home.records.RecordsUiState
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun RecordsSearchBarTitle(
    query: String,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = {
            onScreenAction(
                RecordScreenAction.OnSearchTextUpdate(
                    searchText = it
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_bar_placeholder)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
    )
}

@Composable
fun RecordsSearchBarNavIcon() {
    Icon(
        Icons.Default.Search,
        contentDescription = stringResource(R.string.cd_search_bar),
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
fun RecordsSearchBarActions(
    query: String,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    if (query.isNotEmpty()) {
        IconButton(onClick = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onScreenAction(
                RecordScreenAction.OnSearchTextUpdate(
                    searchText = ""
                )
            )
        }) {
            Icon(
                Icons.Default.Clear,
                contentDescription = stringResource(R.string.cd_clear_search_bar)
            )
        }
    }
    IconButton(
        onClick = {
            onScreenAction(
                RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                    showAddNewRecordBottomSheet = true
                )
            )
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(R.string.cd_add_new_record_button)
        )
    }
}

private fun getAppBarConfig(
    uiState: RecordsUiState,
    onScreenAction: (RecordScreenAction) -> Unit,
): TopAppBarConfig {
    return TopAppBarConfig(
        title = { RecordsSearchBarTitle(uiState.searchText, onScreenAction) },
        navigationIcon = { RecordsSearchBarNavIcon() },
        actions = { RecordsSearchBarActions(uiState.searchText, onScreenAction) },
        scrollBehaviorType = ScrollBehaviorType.ENTER_ALWAYS
    )
}

@LightDarkModePreview
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RecordsSearchBarEmptyPreview() {
    SafeBoxTheme {
        MyAppTopAppBar(
            config = getAppBarConfig(
                uiState = RecordsUiState(searchText = ""),
                onScreenAction = {}
            )
        )
    }
}

@LightDarkModePreview
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RecordsSearchBarWithSearchTextPreview() {
    MyAppTopAppBar(
        config = getAppBarConfig(
            uiState = RecordsUiState(searchText = "abc"),
            onScreenAction = {}
        )
    )
}
