package com.andryoga.composeapp.ui.home.records.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R

@Composable
fun RecordsSearchBar(
    query: String,
    onSearchTextUpdate: (String) -> Unit,
    onClearSearchText: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        TextField(
            value = query,
            onValueChange = onSearchTextUpdate,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_bar_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search_bar)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearSearchText) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search_bar)
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
@Preview
private fun RecordsSearchBarEmptyPreview() {
    RecordsSearchBar(query = "", onSearchTextUpdate = {}, onClearSearchText = {})
}

@Composable
@Preview
private fun RecordsSearchBarWithSearchTextPreview() {
    RecordsSearchBar(query = "abc", onSearchTextUpdate = {}, onClearSearchText = {})
}