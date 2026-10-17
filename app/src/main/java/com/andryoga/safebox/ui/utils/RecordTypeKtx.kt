package com.andryoga.safebox.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.domain.models.record.RecordType

@Composable
fun RecordType.getTitle(): String {
    return stringResource(
        when (this) {
            RecordType.LOGIN -> R.string.type_display_login
            RecordType.CARD -> R.string.type_display_card
            RecordType.BANK_ACCOUNT -> R.string.type_display_account
            RecordType.NOTE -> R.string.type_display_note
            RecordType.AUTHENTICATOR -> R.string.type_display_authenticator
        }
    )
}

fun RecordType.getIcon(): ImageVector {
    return when (this) {
        RecordType.LOGIN -> Icons.AutoMirrored.Filled.Login
        RecordType.CARD -> Icons.Filled.CreditCard
        RecordType.BANK_ACCOUNT -> Icons.Filled.AccountBalance
        RecordType.NOTE -> Icons.AutoMirrored.Filled.Note
        RecordType.AUTHENTICATOR -> Icons.Filled.Password
    }
}

/** One line on what the type holds, shown under [getTitle] where a bare type name is not enough. */
@Composable
fun RecordType.getDescription(): String {
    return stringResource(
        when (this) {
            RecordType.LOGIN -> R.string.record_type_desc_login
            RecordType.CARD -> R.string.record_type_desc_card
            RecordType.BANK_ACCOUNT -> R.string.record_type_desc_bank_account
            RecordType.NOTE -> R.string.record_type_desc_note
            RecordType.AUTHENTICATOR -> R.string.record_type_desc_authenticator
        }
    )
}