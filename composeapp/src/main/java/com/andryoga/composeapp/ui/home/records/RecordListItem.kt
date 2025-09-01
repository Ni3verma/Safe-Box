package com.andryoga.composeapp.ui.home.records

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.ui.graphics.vector.ImageVector
import com.andryoga.composeapp.R

data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val type: RecordListItem.Type
) {
    enum class Type(
        val icon: ImageVector,
        @param:StringRes val titleResId: Int
    ) {
        LOGIN(
            icon = Icons.AutoMirrored.Filled.Login,
            titleResId = R.string.login
        ),
        CARD(
            icon = Icons.Filled.CreditCard,
            titleResId = R.string.card
        ),
        BANK_ACCOUNT(
            icon = Icons.Filled.AccountBalance,
            titleResId = R.string.bank
        ),
        NOTE(
            icon = Icons.AutoMirrored.Filled.Note,
            titleResId = R.string.note
        )
    }
}
