package com.andryoga.composeapp.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.andryoga.composeapp.R
import com.andryoga.composeapp.domain.models.record.RecordType

@Composable
fun RecordType.getTitle(): String {
    return stringResource(
        when (this) {
            RecordType.LOGIN -> R.string.type_display_login
            RecordType.CARD -> R.string.type_display_card
            RecordType.BANK_ACCOUNT -> R.string.type_display_account
            RecordType.NOTE -> R.string.type_display_note
        }
    )
}

fun RecordType.getIcon(): ImageVector {
    return when (this) {
        RecordType.LOGIN -> Icons.AutoMirrored.Filled.Login
        RecordType.CARD -> Icons.Filled.CreditCard
        RecordType.BANK_ACCOUNT -> Icons.Filled.AccountBalance
        RecordType.NOTE -> Icons.AutoMirrored.Filled.Note
    }
}