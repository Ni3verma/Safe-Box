package com.andryoga.composeapp.ui.home.records

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RecordsScreenRoot() {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    viewModel.loadRecords()
    RecordsScreen(
        uiState = uiState
    )
}

@Composable
private fun RecordsScreen(
    uiState: RecordsUiState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    )
    {
        if (uiState.isLoading.not() && uiState.records.isNullOrEmpty().not()) {
            val records = uiState.records
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = records,
                    key = { it.id }) { record ->
                    RecordItem(record)
                }
            }
        }
    }
}

@Composable
fun RecordItem(item: RecordListItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(text = item.subTitle ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showSystemUi = true)
@Preview(showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordsScreenPreview() {
    val records = mutableListOf<RecordListItem>()
    repeat(30) {
        records.add(
            RecordListItem(
                it,
                "$it - title", "$it - subtitle", RecordListItem.Type.LOGIN
            )
        )
    }
    RecordsScreen(
        uiState = RecordsUiState(isLoading = false, records = records),
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordItemPreview() {
    RecordItem(item = RecordListItem(1, "title", "subtitle", RecordListItem.Type.LOGIN))

}