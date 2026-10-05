@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.safebox.ui.home.records.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.home.records.RecordScreenAction
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Top app bar of the records screen: a search field as the title, flanked by the search icon and
 * the clear / add-record actions.
 *
 * @param searchTextState the query, owned by `RecordsViewModel` so it outlives this bar. The field
 * edits it directly; see [RecordsSearchBarTitle] for why it is not a `String` + callback.
 * @param scrollBehavior lets the bar collapse while the records list scrolls.
 */
@ExperimentalMaterial3Api
@Composable
fun RecordsTopAppBar(
    searchTextState: TextFieldState,
    onScreenAction: (RecordScreenAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    MyAppTopAppBar(
        title = { RecordsSearchBarTitle(searchTextState) },
        navigationIcon = { RecordsSearchBarNavIcon() },
        actions = { RecordsSearchBarActions(searchTextState, onScreenAction) },
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The search field. It is state-based rather than `value`/`onValueChange` on purpose: a
 * value-based field must be handed its new value synchronously, and the records query is
 * consumed by a `combine` running off the main thread. Feeding the field from that pipeline's
 * output lagged behind fast typing and dropped the caret one position short of the end.
 */
@Composable
fun RecordsSearchBarTitle(
    searchTextState: TextFieldState,
) {
    TextField(
        state = searchTextState,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_bar_placeholder)) },
        lineLimits = TextFieldLineLimits.SingleLine,
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
    searchTextState: TextFieldState,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    if (searchTextState.text.isNotEmpty()) {
        IconButton(onClick = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onScreenAction(RecordScreenAction.OnClearSearchText)
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

@LightDarkModePreview
@Composable
private fun RecordsSearchBarEmptyPreview() {
    SafeBoxTheme {
        RecordsTopAppBar(
            searchTextState = rememberTextFieldState(),
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecordsSearchBarWithSearchTextPreview() {
    SafeBoxTheme {
        RecordsTopAppBar(
            searchTextState = rememberTextFieldState("abc"),
            onScreenAction = {},
        )
    }
}
