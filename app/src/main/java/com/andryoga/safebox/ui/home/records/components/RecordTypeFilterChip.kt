package com.andryoga.safebox.ui.home.records.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.home.records.RecordsUiState
import com.andryoga.safebox.ui.home.records.RecordsUiState.RecordTypeFilter
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.getTitle

@Composable
fun RecordTypeFilterRow(
    filters: List<RecordsUiState.RecordTypeFilter>,
    onFilterToggle: (RecordType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(scrollState)
    ) {
        filters.forEach {
            RecordTypeFilterChip(
                recordType = it.recordType,
                isSelected = it.isSelected,
                onClick = { onFilterToggle(it.recordType) }
            )
        }
    }
}

@Composable
fun RecordTypeFilterChip(
    recordType: RecordType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = {
            Text(recordType.getTitle())
        },
        selected = isSelected,
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

@LightDarkModePreview
@Composable
fun RecordTypeFilterChipNotSelectedPreview() {
    SafeBoxTheme {
        RecordTypeFilterChip(
            recordType = RecordType.LOGIN,
            isSelected = false,
            onClick = {})
    }
}

@LightDarkModePreview
@Composable
fun RecordTypeFilterChipSelectedPreview() {
    SafeBoxTheme {
        RecordTypeFilterChip(
            recordType = RecordType.LOGIN,
            isSelected = true,
            onClick = {})
    }
}

@LightDarkModePreview
@Composable
fun RecordTypeFilterRowPreview() {
    SafeBoxTheme {
        RecordTypeFilterRow(
            filters = listOf(
                RecordTypeFilter(RecordType.LOGIN, false),
                RecordTypeFilter(RecordType.CARD, false),
                RecordTypeFilter(RecordType.BANK_ACCOUNT, false),
                RecordTypeFilter(RecordType.NOTE, false)
            ),
            onFilterToggle = {}
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordTypeFilterRowSomeSelectedPreview() {
    SafeBoxTheme {
        RecordTypeFilterRow(
            filters = listOf(
                RecordTypeFilter(RecordType.LOGIN, false),
                RecordTypeFilter(RecordType.CARD, true),
                RecordTypeFilter(RecordType.BANK_ACCOUNT, false),
                RecordTypeFilter(RecordType.NOTE, true)
            ),
            onFilterToggle = {}
        )
    }
}