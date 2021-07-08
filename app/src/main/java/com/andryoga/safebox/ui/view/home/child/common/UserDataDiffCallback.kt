package com.andryoga.safebox.ui.view.home.child.common

import androidx.recyclerview.widget.DiffUtil

class UserDataDiffCallback : DiffUtil.ItemCallback<UserDataAdapterEntity>() {
    override fun areItemsTheSame(
        oldItem: UserDataAdapterEntity,
        newItem: UserDataAdapterEntity
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: UserDataAdapterEntity,
        newItem: UserDataAdapterEntity
    ): Boolean {
        return oldItem == newItem
    }
}
