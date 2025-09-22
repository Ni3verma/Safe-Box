@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.composeapp.ui.home.records.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R

@Composable
fun RecordsSearchBar(
    query: String,
    onSearchTextUpdate: (String) -> Unit,
    onClearSearchText: () -> Unit,
    onAddNewRecordButtonTap: () -> Unit,
    topAppBarScrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onSearchTextUpdate,
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
        },
        navigationIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search_bar),
                modifier = Modifier.padding(start = 16.dp)
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onClearSearchText()
                }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search_bar)
                    )
                }
            }
            IconButton(
                onClick = onAddNewRecordButtonTap,
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
        },
        windowInsets = WindowInsets.statusBars,
        scrollBehavior = topAppBarScrollBehavior,
    )
}

@Composable
@Preview
private fun RecordsSearchBarEmptyPreview() {
    RecordsSearchBar(
        query = "",
        onSearchTextUpdate = {},
        onAddNewRecordButtonTap = {},
        onClearSearchText = {},
    )
}

@Composable
@Preview
private fun RecordsSearchBarWithSearchTextPreview() {
    RecordsSearchBar(
        query = "abc",
        onSearchTextUpdate = {},
        onAddNewRecordButtonTap = {},
        onClearSearchText = {},
    )
}
