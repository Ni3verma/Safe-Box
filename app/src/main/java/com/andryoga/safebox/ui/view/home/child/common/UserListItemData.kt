package com.andryoga.safebox.ui.view.home.child.common

data class UserListItemData(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val type: UserDataType
)

enum class UserDataType { LOGIN_DATA, BANK_ACCOUNT, BANK_CARD, SECURE_NOTE }
