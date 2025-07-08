package com.andryoga.safebox.ui.view.home.child.common

import com.andryoga.safebox.ui.common.UserDataType

data class UserListItemData(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val type: UserDataType
)
